package com.hytech.training.domain;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class RecruitmentModels {
    private RecruitmentModels() {}

    public static final List<String> STATUSES = List.of(
            "EMAIL_CONTACT", "EMAIL_COMMUNICATING", "TIME_CONFIRMING",
            "INTERVIEW_CONFIRMED", "REJECTED", "SECOND_INVITATION_NO_RESPONSE");

    public record Candidate(String name, String email, String phone, String position) {}
    public record StatusHistory(String fromStatus, String toStatus, String actorRole,
                                String actorName, OffsetDateTime at, String reason) {}
    public record ArrangementHistory(String actorRole, String actorName, List<String> fields,
                                     OffsetDateTime at) {}
    public record AuditEvent(String action, String role, String result, String recordId,
                             String reason, OffsetDateTime at) {}
    public record ScheduleRun(String id, String role, String actorName, OffsetDateTime at,
                              int createdCount, int eligibleCount) {}

    public static final class RecruitmentRecord {
        public String id;
        public Candidate candidate;
        public OffsetDateTime keyinAt;
        public OffsetDateTime candidateRespondedAt;
        public OffsetDateTime secondInvitationReminderAt;
        public OffsetDateTime secondInvitationAt;
        public String secondInvitationResult;
        public String status;
        public OffsetDateTime interviewAt;
        public boolean interviewCancelled;
        public String rejectionType;
        public String rejectionReason;
        public String linkedOriginalCaseId;
        public String newCaseReason;
        public List<StatusHistory> statusHistory = new ArrayList<>();
        public List<String> interviewManagers = new ArrayList<>();
        public String interviewLocation = "";
        public String interviewNotes = "";
        public List<OffsetDateTime> expectedTimeSlots = new ArrayList<>();
        public List<ArrangementHistory> arrangementHistory = new ArrayList<>();

        public RecruitmentRecord() {}
    }

    public static final class RecruitmentState {
        public int schemaVersion = 1;
        public List<RecruitmentRecord> records = new ArrayList<>();
        public List<AuditEvent> auditEvents = new ArrayList<>();
        public List<ScheduleRun> scheduleRuns = new ArrayList<>();

        public RecruitmentState() {}
    }
}
