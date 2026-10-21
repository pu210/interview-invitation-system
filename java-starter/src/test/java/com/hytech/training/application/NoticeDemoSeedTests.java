package com.hytech.training.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hytech.training.infrastructure.RecruitmentStateRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NoticeDemoSeedTests {
    @TempDir Path temp;

    @Test void noticeSeedAndResetHaveSelectableTimeAndChronologicalHistoryAfterReload() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var repository = new RecruitmentStateRepository(mapper, temp.resolve("state.json").toString());
        var clock = Clock.fixed(Instant.parse("2026-10-21T08:00:00Z"), ZoneId.of("Asia/Taipei"));
        var service = new RecruitmentService(repository, clock);
        for (int attempt = 0; attempt < 2; attempt++) {
            var notice = service.record("REC-DEMO-NOTICE");
            assertEquals(List.of(notice.interviewAt), notice.expectedTimeSlots);
            assertEquals("INTERVIEW_CONFIRMED", notice.status);
            assertEquals(notice.candidateRespondedAt, notice.statusHistory.getLast().at());
            var previous = notice.keyinAt;
            for (var event : notice.statusHistory) {
                assertFalse(event.at().isBefore(previous));
                assertFalse(event.at().toInstant().isAfter(clock.instant()));
                previous = event.at();
            }
            var preview = service.previewNotification(notice.id, new RecruitmentService.ScheduleRequest("HR", "HR"));
            assertTrue(preview.errors().isEmpty());
            String saved = mapper.writeValueAsString(notice);
            service = new RecruitmentService(repository, clock);
            assertEquals(saved, mapper.writeValueAsString(service.record(notice.id)));
            // Confirming the existing selected slot must pass the real arrangement policy.
            service.updateArrangement(notice.id, new RecruitmentService.ArrangementUpdateRequest(
                    "HR", "HR", List.of("confirmedInterviewAt"), null, null, null, null, notice.interviewAt.toString()));
            service.reset();
        }
    }
}
