package com.hytech.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hytech.training.application.RecruitmentService;
import com.hytech.training.infrastructure.RecruitmentStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "recruitment.data-file=target/test-data/bug-fix-api.json")
@AutoConfigureMockMvc
class BugFixApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RecruitmentService service;
    @Autowired RecruitmentStateRepository repository;
    private static final String ID = "REC-20261012-002";
    private static final String BASE = "/api/recruitment/records/" + ID;
    @BeforeEach void reset() { service.reset(); }

    @Test void bothConfirmationApisRejectPastAndUnavailableTimesWithoutPartialWrites() throws Exception {
        var available = OffsetDateTime.now().plusDays(5).withNano(0);
        var past = OffsetDateTime.now().minusMinutes(1).withNano(0);
        mvc.perform(put(BASE + "/arrangement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                Map.of("role","MANAGER","actorName","主管甲","changedFields",List.of("expectedTimeSlots"),
                        "expectedTimeSlots",List.of(available.toString(),past.toString()))))).andExpect(status().isOk());
        String before = mapper.writeValueAsString(repository.load().records);
        for (String time : List.of(past.toString(), available.plusHours(1).toString())) {
            mvc.perform(patch(BASE + "/status").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                    Map.of("role","HR","actorName","HR","newStatus","INTERVIEW_CONFIRMED","interviewAt",time))))
                    .andExpect(status().isUnprocessableEntity());
            mvc.perform(put(BASE + "/arrangement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                    Map.of("role","HR","actorName","HR","changedFields",List.of("interviewLocation","confirmedInterviewAt"),
                            "interviewLocation","板橋","confirmedInterviewAt",time))))
                    .andExpect(status().isUnprocessableEntity());
            assertEquals(before, mapper.writeValueAsString(repository.load().records));
        }
        // The same instant with a different offset must still match the manager's slot.
        String utc = available.toInstant().toString();
        mvc.perform(patch(BASE + "/status").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                Map.of("role","HR","actorName","確認 HR","newStatus","INTERVIEW_CONFIRMED","interviewAt",utc))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INTERVIEW_CONFIRMED"));
        var stored = repository.load().records.stream().filter(r -> r.id.equals(ID)).findFirst().orElseThrow();
        assertEquals(available.toInstant(), stored.interviewAt.toInstant());
        assertEquals("確認 HR", stored.statusHistory.getLast().actorName());
    }

    @Test void noManagerSlotsCannotConfirm() throws Exception {
        service.record("REC-20260928-003").keyinAt = OffsetDateTime.now().minusDays(2);
        repository.save(service.state());
        String before = mapper.writeValueAsString(repository.load().records);
        mvc.perform(put(BASE + "/arrangement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                Map.of("role","HR","actorName","HR","changedFields",List.of("confirmedInterviewAt"),
                        "confirmedInterviewAt",OffsetDateTime.now().plusDays(1).toString()))))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(before, mapper.writeValueAsString(repository.load().records));
    }

    @Test void aggregateStateDoesNotExposeAuditOrScheduleToManagerOrAnonymous() throws Exception {
        service.runSchedule(new RecruitmentService.ScheduleRequest("HR", "HR"));
        for (String query : List.of("", "?role=MANAGER"))
            mvc.perform(get("/api/recruitment" + query)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.records").isNotEmpty())
                    .andExpect(jsonPath("$.auditEvents").isEmpty())
                    .andExpect(jsonPath("$.scheduleRuns").isEmpty());
        mvc.perform(get("/api/recruitment?role=HR")).andExpect(status().isOk())
                .andExpect(jsonPath("$.auditEvents").isNotEmpty());
        mvc.perform(get("/api/recruitment/audit-events?role=MANAGER")).andExpect(status().isUnprocessableEntity());
    }

    @Test void smuggledFieldsAreRejectedAsAWholeIncludingNullAndUnknownFields() throws Exception {
        String before = mapper.writeValueAsString(repository.load().records);
        for (String extra : List.of("\"confirmedInterviewAt\":null", "\"confirmedInterviewAt\":\"2099-11-01T10:00:00+08:00\"",
                "\"status\":\"REJECTED\"", "\"candidate\":{}", "\"unknownField\":true")) {
            mvc.perform(put(BASE + "/arrangement").contentType(MediaType.APPLICATION_JSON).content(
                    "{\"role\":\"MANAGER\",\"actorName\":\"主管\",\"changedFields\":[\"interviewNotes\"],\"interviewNotes\":\"不得保存\"," + extra + "}"))
                    .andExpect(status().isUnprocessableEntity());
            assertEquals(before, mapper.writeValueAsString(repository.load().records));
        }
        assertTrue(repository.load().auditEvents.stream().anyMatch(e -> "OPERATION_BLOCKED".equals(e.action())));
    }

    @Test void secondInvitationStatusRequiresExecutionTimeAndPersistsHistory() throws Exception {
        String before = mapper.writeValueAsString(repository.load().records);
        mvc.perform(patch(BASE + "/status").contentType(MediaType.APPLICATION_JSON).content(
                "{\"role\":\"HR\",\"actorName\":\"HR\",\"newStatus\":\"SECOND_INVITATION_NO_RESPONSE\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(before, mapper.writeValueAsString(repository.load().records));
        String invited = "/api/recruitment/records/REC-20261017-006/status";
        mvc.perform(patch(invited).contentType(MediaType.APPLICATION_JSON).content(
                "{\"role\":\"HR\",\"actorName\":\"HR\",\"newStatus\":\"EMAIL_CONTACT\"}"))
                .andExpect(status().isOk());
        mvc.perform(patch(invited).contentType(MediaType.APPLICATION_JSON).content(
                "{\"role\":\"HR\",\"actorName\":\"HR\",\"newStatus\":\"SECOND_INVITATION_NO_RESPONSE\"}"))
                .andExpect(status().isOk());
        assertNotNull(new RecruitmentService(repository).record("REC-20261017-006").secondInvitationAt);
    }

    @Test void rollbackDeletesCalendarAndTimeAndCanReconfirmWithoutResendingEmail() throws Exception {
        String id = "REC-DEMO-NOTICE", base = "/api/recruitment/records/" + id;
        var request = new RecruitmentService.NotificationRequest("HR", "HR", false, false);
        service.executeNotification(id, request);
        var originalTime = service.record(id).interviewAt;
        String oldEvent = service.record(id).notificationTask.event.id;
        mvc.perform(patch(base + "/status").contentType(MediaType.APPLICATION_JSON).content(
                "{\"role\":\"HR\",\"actorName\":\"HR\",\"newStatus\":\"TIME_CONFIRMING\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.interviewAt").isEmpty())
                .andExpect(jsonPath("$.notificationTask.event").isEmpty());
        var restored = new RecruitmentService(repository);
        assertNull(restored.record(id).interviewAt);
        assertNull(restored.record(id).notificationTask.event);
        assertEquals("成功", restored.record(id).notificationTask.emailStatus);
        mvc.perform(put(base + "/arrangement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                Map.of("role", "HR", "actorName", "HR", "changedFields", List.of("expectedTimeSlots", "confirmedInterviewAt"),
                        "expectedTimeSlots", List.of(originalTime.toString()), "confirmedInterviewAt", originalTime.toString()))))
                .andExpect(status().isOk());
        service.executeNotification(id, request);
        var task = new RecruitmentService(repository).record(id).notificationTask;
        assertNotEquals(oldEvent, task.event.id);
        assertEquals(1, task.attempts.stream().filter(a -> "EMAIL".equals(a.item()) && "成功".equals(a.result())).count());
    }
}
