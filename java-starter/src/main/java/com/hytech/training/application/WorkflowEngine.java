package com.hytech.training.application;

import com.hytech.training.domain.Models.*;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public final class WorkflowEngine {
    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.parse("2026-09-01T09:00:00+08:00");

    public ScenarioState execute(ScenarioDefinition scenario, Candidate candidate, ScenarioState current) {
        // TODO(Day 2)：依 docs/scope-matrix.md 完成六個 P0。
        // 1. 用 candidateId／operationId 判斷同一件工作，重跑不得重複建立外部結果。
        // 2. 使用 FIXED_NOW 判斷 90 天重複邀約，不得改用 OffsetDateTime.now()。
        // 3. 只透過 MailRecord／CalendarRecord／DriveRecord 模擬外部服務。
        // 4. P0 完成後解除 WorkflowContractTests.java 的 @Disabled。
        List<AuditEvent> audit = new ArrayList<>(current.auditEvents());
        audit.add(new AuditEvent("WORKFLOW_NOT_IMPLEMENTED", "FAILED", "NOT_IMPLEMENTED", FIXED_NOW));
        return new ScenarioState(scenario.scenarioId(), candidate.candidateId(), scenario.operationId(), "NOT_IMPLEMENTED",
                List.of("IMPLEMENT_P0_RULE"), current.mailRecords(), current.calendarRecords(), current.driveRecords(), List.copyOf(audit));
    }
}
