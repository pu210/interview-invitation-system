package com.hytech.training.application;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public final class InterviewArrangementPolicy {
    private static final Set<String> MANAGER_FIELDS = Set.of(
            "interviewManagers", "interviewLocation", "interviewNotes", "expectedTimeSlots");
    private static final Set<String> HR_FIELDS = Set.of(
            "interviewManagers", "interviewLocation", "interviewNotes", "expectedTimeSlots", "confirmedInterviewAt");

    public ValidationResult validate(UpdateRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!Set.of("HR", "MANAGER").contains(request.role())) errors.put("role", "角色無效。只允許 HR 或 MANAGER。");
        if (request.closed()) errors.put("record", "案件已結案，不可修改面試安排。");
        Set<String> allowed = "MANAGER".equals(request.role()) ? MANAGER_FIELDS : HR_FIELDS;
        for (String field : request.changedFields() == null ? List.<String>of() : request.changedFields()) {
            if (!allowed.contains(field)) errors.put(field, "目前角色無權修改此欄位。");
        }
        List<String> managers = request.interviewManagers() == null ? List.of() : request.interviewManagers();
        long distinctManagers = managers.stream().map(String::trim).filter(value -> !value.isEmpty()).distinct().count();
        if (distinctManagers != managers.stream().map(String::trim).filter(value -> !value.isEmpty()).count())
            errors.put("interviewManagers", "面試主管不可重複。");
        if (distinctManagers > 3) errors.put("interviewManagers", "同一案件最多三位不同主管。");
        if (request.interviewLocation() != null && !request.interviewLocation().isBlank()
                && !Set.of("南港", "板橋").contains(request.interviewLocation()))
            errors.put("interviewLocation", "面試地點僅限南港或板橋。");
        if ("MANAGER".equals(request.role()) && "TIME_CONFIRMING".equals(request.status())
                && request.changedFields() != null && request.changedFields().contains("expectedTimeSlots"))
            errors.put("expectedTimeSlots", "時間確認中，主管不可變更期待安排時間。");
        if (request.expectedTimeSlots() != null) {
            for (String slot : request.expectedTimeSlots()) {
                try { OffsetDateTime.parse(slot); }
                catch (RuntimeException ex) { errors.put("expectedTimeSlots", "期待安排時間必須是可解析且非空的日期時間。"); break; }
            }
        }
        if (request.changedFields() != null && request.changedFields().contains("confirmedInterviewAt")) {
            if (!"HR".equals(request.role())) errors.put("confirmedInterviewAt", "只有 HR 可以確認最終面試時間。");
            try {
                if (request.confirmedInterviewAt() == null || request.confirmedInterviewAt().isBlank()) throw new IllegalArgumentException();
                OffsetDateTime.parse(request.confirmedInterviewAt());
            } catch (RuntimeException ex) { errors.put("confirmedInterviewAt", "請提供有效的最終面試時間。"); }
        }
        return new ValidationResult(errors.isEmpty(), errors);
    }

    public record UpdateRequest(String role, String status, boolean closed, List<String> changedFields,
                                List<String> interviewManagers, String interviewLocation,
                                List<String> expectedTimeSlots, String confirmedInterviewAt) {}
    public record ValidationResult(boolean valid, Map<String, String> errors) {}
}
