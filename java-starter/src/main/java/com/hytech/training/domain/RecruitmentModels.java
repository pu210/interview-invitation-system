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
                             String reason, OffsetDateTime at, String actorName) {}
    public record ScheduleRun(String id, String role, String actorName, OffsetDateTime at,
                              int createdCount, int eligibleCount) {}

    public record NotificationDetails(String templateId, String surname, String salutation,
                                      String preparationUrl, String meetingUrl, String resumeUrl, String hrEmail) {}
    public record NotificationAttempt(String item, String result, String reason, String actorName, OffsetDateTime at) {}
    public static final class NotificationTask {
        public String id;
        public String emailStatus = "未執行";
        public String calendarStatus = "未執行";
        public String emailFailure;
        public String calendarFailure;
        public String to;
        public List<String> cc = new ArrayList<>();
        public String html;
        public CalendarEvent event;
        public List<NotificationAttempt> attempts = new ArrayList<>();
    }
    public static final class CalendarEvent {
        public String id;
        public String status = "有效";
        public String hrEmail;
        public List<String> managers = new ArrayList<>();
        public List<String> managerEmails = new ArrayList<>();
        public OffsetDateTime interviewAt;
        public String location;
        public String meetingUrl;
        public String resumeUrl;
        public OffsetDateTime updatedAt;
    }

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
        public NotificationDetails notificationDetails;
        public NotificationTask notificationTask;

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
