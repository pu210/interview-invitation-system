package com.hytech.training.domain;

import java.time.OffsetDateTime;
import java.util.List;

public final class Models {
    private Models() {}
    public record Candidate(String candidateId, String source, String resumeId, String name, String email, String appliedRole,
                            OffsetDateTime firstInvitedAt, OffsetDateTime secondInvitedAt, OffsetDateTime lastInvitedAt,
                            String supervisorDecision, String candidateDecision, OffsetDateTime interviewAt, int interviewRound) {}
    public record ScenarioDefinition(String scenarioId, String priority, String title, String candidateId, String operationId,
                                     boolean required, String simulatedFailure) {}
    public record ExpectedResult(String scenarioId, String status, int mailCount, int calendarCount, int driveFileCount,
                                 List<String> nextActions) {}
    public record MailRecord(String mailType, String status, int attempt) {}
    public record CalendarRecord(String action, String status, String eventId) {}
    public record DriveRecord(String documentType, String status, String path) {}
    public record AuditEvent(String eventType, String status, String errorCode, OffsetDateTime occurredAt) {}
    public record ScenarioState(String scenarioId, String candidateId, String operationId, String status,
                                List<String> nextActions, List<MailRecord> mailRecords, List<CalendarRecord> calendarRecords,
                                List<DriveRecord> driveRecords, List<AuditEvent> auditEvents) {}
}
