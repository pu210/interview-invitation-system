package com.hytech.training.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hytech.training.domain.RecruitmentModels.*;
import com.hytech.training.infrastructure.RecruitmentStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import static com.hytech.training.application.RecruitmentService.*;
import static com.hytech.training.domain.RecruitmentModels.STATUSES;
import static org.junit.jupiter.api.Assertions.*;

class DuplicateInvitationTests {
    @TempDir Path temp;
    final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    final Instant instant = Instant.parse("2026-10-21T02:00:00Z");
    RecruitmentStateRepository repo() { return new RecruitmentStateRepository(mapper, temp.resolve("state.json").toString()); }
    RecruitmentService service() { return new RecruitmentService(repo(), Clock.fixed(instant, ZoneId.of("Asia/Taipei"))); }
    CreateRequest input(String name, String email, String phone, boolean confirmed) {
        return new CreateRequest("HR", "驗收 HR", name, email, phone, "工程師", confirmed);
    }
    String json(Object value) throws Exception { return mapper.writeValueAsString(value); }

    @Test void emailHasPriorityAcrossEveryStatusAndCannotBeOverridden() {
        var service = service();
        for (String status : STATUSES) {
            var original = service.record("REC-20261018-001");
            original.status = status;
            original.candidate = new Candidate("林晨宇", " CHENYU.LIN@example.invalid ", "0911-000-101", "工程師");
            int count = service.state().records.size();
            assertThrows(BusinessException.class, () -> service.create(input("新姓名", "chenyu.lin@EXAMPLE.invalid", "0988000000", true)));
            assertEquals(count, repo().load().records.size());
        }
    }

    @Test void precise30DayBoundaryAndOlderRecords() {
        var service = service();
        var original = service.record("REC-20261018-001");
        original.keyinAt = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC).minusDays(30);
        var request = input("新姓名", original.candidate.email(), "0988000000", false);
        assertThrows(BusinessException.class, () -> service.create(request));
        original.keyinAt = original.keyinAt.minusNanos(1);
        assertNotNull(service.create(request));
    }

    @Test void possibleMatchesIncludeAllCasesAndBothDecisionsPersist() {
        var service = service();
        var request = input("陳雨晴", "new@example.invalid", "0911000101", false);
        var warning = assertThrows(DuplicateException.class, () -> service.create(request));
        assertEquals(2, warning.matches.size()); // Phone and name match different records.
        assertEquals("林晨宇", warning.matches.getFirst().candidate().name());
        int count = service.state().records.size();
        service.confirmSamePerson(request);
        assertEquals(count, service().state().records.size());
        assertTrue(service().state().auditEvents.stream().anyMatch(e -> e.result().equals("SAME_PERSON")));
        var created = service.create(input(request.name(), request.email(), request.phone(), true));
        assertEquals(count + 1, service().state().records.size());
        assertTrue(service().state().auditEvents.stream().anyMatch(e -> e.result().equals("DIFFERENT_PERSON") && created.id.equals(e.recordId())));
        assertNull(created.notificationTask);
    }

    @Test void blankLegacyFieldsAndNoMatchDoNotProduceFalseDuplicates() {
        var service = service();
        service.record("REC-20261018-001").candidate = new Candidate(null, null, null, "舊資料");
        assertNotNull(service.create(input("新姓名", "new@example.invalid", "0988000000", false)));
        int count = service.state().records.size();
        assertThrows(BusinessException.class, () -> service.confirmSamePerson(input("無重複", "other@example.invalid", "0988000001", false)));
        assertEquals(count, service().state().records.size());
        assertThrows(BusinessException.class, () -> service.create(input("", "empty@example.invalid", "0988000002", false)));
    }

    @Test void closedCasesRejectAllMutationsWithoutBusinessSideEffectsAndFollowUpIsIndependent() throws Exception {
        var service = service();
        String id = "REC-DEMO-NOTICE";
        service.executeNotification(id, new NotificationRequest("HR", "HR", false, false));
        var original = service.record(id);
        original.interviewAt = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC).minusSeconds(1);
        repo().save(service.state());
        original.keyinAt = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC).minusDays(31);
        repo().save(service.state());
        String before = json(original);
        for (String role : List.of("HR", "MANAGER")) {
            assertThrows(BusinessException.class, () -> service.updateStatus(id, new StatusUpdateRequest(role,"驗收","EMAIL_CONTACT",null,null,null,"重啟")));
            assertThrows(BusinessException.class, () -> service.updateArrangement(id, new ArrangementUpdateRequest(role,"驗收",List.of("interviewNotes"),null,null,"不能存",null,null)));
            assertThrows(BusinessException.class, () -> service.executeSecondInvitation(id, new SecondInvitationRequest(role,"驗收","不能二邀")));
            assertThrows(BusinessException.class, () -> service.executeNotification(id, new NotificationRequest(role,"驗收",false,false)));
            assertThrows(BusinessException.class, () -> service.updateNotificationDetails(id, new NotificationDetailsRequest(role,"驗收",null)));
            assertEquals(before, json(service().record(id)));
        }
        int count = service.state().records.size();
        assertThrows(BusinessException.class, () -> service.createFollowUp(id, new FollowUpRequest("MANAGER","主管",true,"越權")));
        assertThrows(BusinessException.class, () -> service.createFollowUp(id, new FollowUpRequest("HR","HR",false,"未確認")));
        assertThrows(BusinessException.class, () -> service.createFollowUp(id, new FollowUpRequest("HR","HR",true," ")));
        assertEquals(count, service.state().records.size());
        var follow = service.createFollowUp(id, new FollowUpRequest("HR","HR",true,"新職缺"));
        assertNotEquals(id, follow.id);
        assertEquals(id, follow.linkedOriginalCaseId);
        assertEquals(instant, follow.keyinAt.toInstant());
        assertEquals("EMAIL_CONTACT", follow.status);
        assertNull(follow.notificationTask);
        assertNull(follow.interviewAt);
        assertTrue(follow.interviewManagers.isEmpty());
        assertEquals(before, json(service().record(id)));
        assertEquals(id, service().record(follow.id).linkedOriginalCaseId);
    }

    @Test void cancelledInterviewDoesNotCountAsCompletedAndManagersCannotCreateOrDecide() {
        var service = service();
        var record = service.record("REC-DEMO-NOTICE");
        record.interviewAt = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC).minusSeconds(1);
        record.interviewCancelled = true;
        assertFalse(service.isClosed(record));
        var request = new CreateRequest("MANAGER", "主管", "林晨宇", "new@example.invalid", "0911000101", "工程師", true);
        assertThrows(BusinessException.class, () -> service.create(request));
        assertThrows(BusinessException.class, () -> service.confirmSamePerson(request));
        assertThrows(BusinessException.class, () -> service.createFollowUp(record.id, new FollowUpRequest("HR","HR",true,"未結案")));
    }
}
