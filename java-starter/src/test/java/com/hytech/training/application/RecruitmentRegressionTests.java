package com.hytech.training.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hytech.training.domain.RecruitmentModels.*;
import com.hytech.training.infrastructure.RecruitmentStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static com.hytech.training.application.RecruitmentService.*;

class RecruitmentRegressionTests {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private RecruitmentStateRepository repo() { return new RecruitmentStateRepository(mapper, temp.resolve("state.json").toString()); }
    private RecruitmentService service(String time) { return new RecruitmentService(repo(), Clock.fixed(Instant.parse(time), ZoneId.of("Asia/Taipei"))); }

    @Test void secondInvitationStrict24HourBoundaryAndExclusions() {
        RecruitmentService at24 = service("2026-10-21T02:00:00Z");
        RecruitmentRecord boundary = at24.record("REC-20261020-005");
        assertNull(boundary.secondInvitationReminderAt);
        at24.runSchedule(new ScheduleRequest("HR", "HR"));
        assertNull(boundary.secondInvitationReminderAt);
        RecruitmentService later = service("2026-10-21T02:00:01Z");
        assertEquals(1, later.runSchedule(new ScheduleRequest("HR", "HR")).createdCount());
        assertNotNull(later.record(boundary.id).secondInvitationReminderAt);
        assertNull(later.record("REC-20261012-002").secondInvitationReminderAt);
        assertNull(later.record("REC-20260928-003").secondInvitationReminderAt);
        assertEquals(0, later.runSchedule(new ScheduleRequest("HR", "HR")).createdCount());
    }

    @Test void statusForwardBackwardAndManagerWhitelistRemainAtomic() {
        RecruitmentService service = service("2026-10-21T02:00:00Z");
        String id = "REC-20261018-001";
        service.updateStatus(id, new StatusUpdateRequest("HR", "HR", "TIME_CONFIRMING", null, null, null, "前進"));
        assertThrows(BusinessException.class, () -> service.updateArrangement(id, new ArrangementUpdateRequest("MANAGER", "甲", List.of("expectedTimeSlots"), null, null, null, List.of("2026-11-01T10:00:00+08:00"), null)));
        assertTrue(service.record(id).expectedTimeSlots.isEmpty());
        service.updateStatus(id, new StatusUpdateRequest("HR", "HR", "EMAIL_COMMUNICATING", null, null, null, "退回"));
        assertEquals("EMAIL_COMMUNICATING", service("2026-10-21T02:00:00Z").record(id).status);
        service.updateArrangement(id, new ArrangementUpdateRequest("MANAGER", "甲", List.of("interviewManagers", "interviewLocation"), List.of("甲", "乙", "丙"), "南港", null, null, null));
        assertThrows(BusinessException.class, () -> service.updateArrangement(id, new ArrangementUpdateRequest("MANAGER", "乙", List.of("interviewManagers", "interviewLocation"), List.of("甲", "乙", "丙", "丁"), "板橋", null, null, null)));
        assertEquals(3, service.record(id).interviewManagers.size());
        assertEquals("南港", service.record(id).interviewLocation);
        assertThrows(BusinessException.class, () -> service.updateStatus(id, new StatusUpdateRequest("MANAGER", "主管", "REJECTED", null, "公司婉拒", "測試", null)));
    }

    @Test void notificationRetryAfterServiceRestartKeepsTaskAndSuccessfulEmail() {
        RecruitmentService service = service("2026-10-21T02:00:00Z");
        String id = "REC-DEMO-NOTICE";
        NotificationTask first = service.executeNotification(id, new NotificationRequest("HR", "HR", false, true)).notificationTask;
        RecruitmentService restarted = service("2026-10-21T03:00:00Z");
        NotificationTask complete = restarted.executeNotification(id, new NotificationRequest("HR", "HR", true, false)).notificationTask;
        assertEquals(first.id, complete.id);
        assertEquals("成功", complete.emailStatus);
        assertEquals("成功", complete.calendarStatus);
        assertEquals(3, complete.attempts.size());
        RecruitmentService again = service("2026-10-21T03:01:00Z");
        assertEquals(complete.event.id, again.executeNotification(id, new NotificationRequest("HR", "HR", false, false)).notificationTask.event.id);
        assertEquals(3, again.record(id).notificationTask.attempts.size());
    }
}
