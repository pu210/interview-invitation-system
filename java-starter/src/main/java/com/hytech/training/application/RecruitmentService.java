package com.hytech.training.application;

import com.hytech.training.domain.RecruitmentModels.*;
import com.hytech.training.infrastructure.RecruitmentStateRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static com.hytech.training.domain.RecruitmentModels.STATUSES;

@Service
public final class RecruitmentService {
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final Set<String> MANAGER_FIELDS = Set.of(
            "interviewManagers", "interviewLocation", "interviewNotes", "expectedTimeSlots");
    private static final Set<String> HR_FIELDS = Set.of(
            "interviewManagers", "interviewLocation", "interviewNotes", "expectedTimeSlots", "confirmedInterviewAt");
    private final RecruitmentStateRepository repository;
    private final Clock clock;
    private RecruitmentState state;

    @Autowired
    public RecruitmentService(RecruitmentStateRepository repository) {
        this(repository, Clock.system(TAIPEI));
    }

    RecruitmentService(RecruitmentStateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
        this.state = repository.load();
        if (state == null) reset();
    }

    public synchronized RecruitmentState state() { return state; }

    public synchronized RecruitmentRecord record(String id) {
        return state.records.stream().filter(record -> record.id.equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException("找不到指定的招募案件。"));
    }

    public synchronized RecruitmentRecord create(CreateRequest input) {
        return create(input, false);
    }
    private RecruitmentRecord create(CreateRequest input, boolean decisionOnly) {
        if (!"HR".equals(input.role())) throw blocked(null, input.role(), "只有 HR 可以新增招募紀錄。");
        String actor = text(input.actorName(), "缺少操作人。");
        String name = text(input.name(), "姓名為必填。"), email = text(input.email(), "Email 為必填。").toLowerCase(Locale.ROOT);
        String phone = text(input.phone(), "手機為必填。"), position = text(input.position(), "應徵職缺為必填。");
        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) throw new BusinessException("Email 格式不正確。");
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() < 8 || digits.length() > 15) throw new BusinessException("手機格式不正確，請輸入 8 至 15 位數字。");
        OffsetDateTime now = now();
        List<RecruitmentRecord> recent = state.records.stream().filter(record ->
                !record.keyinAt.isAfter(now) && !record.keyinAt.isBefore(now.minusDays(30))).toList();
        recent.stream().filter(record -> same(record.candidate.email(), email)).findFirst().ifPresent(record -> {
            throw blocked(record.id, input.role(), "30 天內重複邀約：Email 與案件 " + record.id + " 相同，無法新增。");
        });
        List<DuplicateMatch> possible = recent.stream().filter(record -> samePhone(record.candidate.phone(), phone)
                || same(record.candidate.name(), name)).map(record -> new DuplicateMatch(record.id, record.candidate,
                record.keyinAt, record.status, samePhone(record.candidate.phone(), phone) ? "手機" : "姓名")).toList();
        if (!possible.isEmpty() && !input.differentPersonConfirmed()) {
            audit("DUPLICATE_CHECK", input.role(), "BLOCKED", null,
                    "可能重複邀約；比對案件：" + possible.stream().map(DuplicateMatch::id).toList(), now); save();
            throw new DuplicateException(possible);
        }
        if (decisionOnly) throw new BusinessException("目前沒有可能重複案件，請重新檢查。");
        RecruitmentRecord created = baseRecord(new Candidate(name, email, phone, position), now);
        created.statusHistory.add(new StatusHistory(null, created.status, input.role(), actor, now, "建立招募紀錄"));
        if (!possible.isEmpty()) audit("DUPLICATE_DECISION", input.role(), "DIFFERENT_PERSON", created.id,
                actor + " 確認不同人；比對案件：" + possible.stream().map(DuplicateMatch::id).toList(), now);
        state.records.add(0, created);
        audit("CREATE_RECORD", input.role(), "SUCCEEDED", created.id, null, now, actor);
        save();
        return created;
    }

    // Same-person confirmation records the decision without creating any business record.
    public synchronized void confirmSamePerson(CreateRequest input) {
        if (!"HR".equals(input.role())) throw blocked(null, input.role(), "只有 HR 可以確認重複邀約。");
        String actor = text(input.actorName(), "缺少操作人。");
        try {
            // Reuse all create validations, but force the duplicate warning before any insertion.
            CreateRequest check = new CreateRequest(input.role(), actor, input.name(), input.email(), input.phone(), input.position(), false);
            create(check, true);
        } catch (DuplicateException duplicate) {
            audit("DUPLICATE_DECISION", input.role(), "SAME_PERSON", null,
                    actor + " 確認同一人，不新增；比對案件：" + duplicate.matches.stream().map(DuplicateMatch::id).toList(), now());
            save();
        }
    }
    private boolean same(String left, String right) { return left != null && !left.isBlank() && right != null && !right.isBlank() && left.trim().equalsIgnoreCase(right.trim()); }
    private boolean samePhone(String left, String right) { return left != null && right != null && same(left.replaceAll("[^0-9]", ""), right.replaceAll("[^0-9]", "")); }

    public synchronized RecruitmentRecord updateStatus(String id, StatusUpdateRequest input) {
        if (!"HR".equals(input.role())) throw blocked(id, input.role(), "主管僅能唯讀查看邀約紀錄，不能修改招募狀態。");
        RecruitmentRecord current = record(id);
        requireOpen(current, input.role());
        String actor = text(input.actorName(), "缺少操作人。");
        if (!STATUSES.contains(input.newStatus())) throw new BusinessException("請選擇有效的招募狀態。");
        if (current.status.equals(input.newStatus())) throw new BusinessException("新狀態與目前狀態相同，沒有需要儲存的異動。");
        OffsetDateTime interviewAt = current.interviewAt;
        String rejectionType = null, rejectionReason = null;
        if ("INTERVIEW_CONFIRMED".equals(input.newStatus())) {
            interviewAt = date(input.interviewAt(), "確認面試時必須填寫有效面試時間。");
            validateConfirmedTime(interviewAt, current.expectedTimeSlots, id, input.role());
        }
        if ("REJECTED".equals(input.newStatus())) {
            rejectionType = text(input.rejectionType(), "設為婉拒時必須選擇婉拒種類。");
            if (!Set.of("求職者婉拒", "公司婉拒").contains(rejectionType)) throw new BusinessException("婉拒種類無效。");
            rejectionReason = text(input.rejectionReason(), "設為婉拒時必須填寫婉拒原因。");
        }
        OffsetDateTime now = now();
        String reason = "REJECTED".equals(input.newStatus()) ? rejectionType + "：" + rejectionReason
                : input.reason() == null || input.reason().isBlank() ? "狀態更新" : input.reason().trim();
        String oldStatus = current.status;
        current.status = input.newStatus(); current.interviewAt = interviewAt;
        current.rejectionType = rejectionType; current.rejectionReason = rejectionReason;
        if (current.candidateRespondedAt == null && Set.of("EMAIL_COMMUNICATING", "TIME_CONFIRMING", "INTERVIEW_CONFIRMED").contains(current.status))
            current.candidateRespondedAt = now;
        current.statusHistory.add(new StatusHistory(oldStatus, current.status, input.role(), actor, now, reason));
        syncCalendar(current, actor);
        audit("UPDATE_STATUS", input.role(), "SUCCEEDED", id, null, now, actor); save(); return current;
    }

    public synchronized RecruitmentRecord createFollowUp(String id, FollowUpRequest input) {
        if (!"HR".equals(input.role())) throw blocked(id, input.role(), "只有 HR 可以對已結案案件另立新案。");
        RecruitmentRecord original = record(id);
        if (!isClosed(original)) throw blocked(id, input.role(), "只有已結案案件可以使用另立新案入口。");
        boolean recentInvitation = state.records.stream().anyMatch(existing ->
                (existing.id.equals(original.id) || same(existing.candidate.email(), original.candidate.email())
                        || samePhone(existing.candidate.phone(), original.candidate.phone()))
                        && existing.keyinAt.isAfter(now().minusDays(30)));
        if (recentInvitation) throw blocked(id, input.role(), "距最近邀約未滿 30 天，無法另立新案。");
        if (!input.confirmed()) throw blocked(id, input.role(), "請先確認需要另立新案。");
        if (input.reason() == null || input.reason().isBlank()) throw blocked(id, input.role(), "另立新案原因為必填。");
        String reason = text(input.reason(), "另立新案原因為必填。"), actor = text(input.actorName(), "缺少操作人。");
        OffsetDateTime now = now(); RecruitmentRecord created = baseRecord(original.candidate, now);
        created.linkedOriginalCaseId = original.id; created.newCaseReason = reason;
        created.statusHistory.add(new StatusHistory(null, created.status, input.role(), actor, now,
                "另立新案：" + reason + "（原案件 " + original.id + "）"));
        state.records.add(0, created); audit("CREATE_FOLLOW_UP_CASE", input.role(), "SUCCEEDED", created.id, null, now, actor); save(); return created;
    }

    public synchronized ScheduleRun runSchedule(ScheduleRequest input) {
        requireHr(input.role(), "只有 HR 可以執行今日排程。請切換為 HR 後再試一次。");
        String actor = text(input.actorName(), "缺少操作人。");
        OffsetDateTime now = now(); int created = 0;
        for (RecruitmentRecord record : state.records) if (needsSecondInvitation(record, now) && record.secondInvitationReminderAt == null) {
            record.secondInvitationReminderAt = now; created++;
            audit("SECOND_INVITATION_REMINDER", "HR", "CREATED", record.id, null, now, actor);
        }
        int eligible = (int) state.records.stream().filter(record -> needsSecondInvitation(record, now)).count();
        ScheduleRun run = new ScheduleRun("SCHEDULE-" + now, input.role(), actor, now, created, eligible);
        state.scheduleRuns.add(run); audit("RUN_SECOND_INVITATION_SCHEDULE", input.role(), "SUCCEEDED", null, null, now, actor); save(); return run;
    }

    @Scheduled(cron = "0 0 10 * * *", zone = "Asia/Taipei")
    public void scheduledRun() { runSchedule(new ScheduleRequest("HR", "系統每日排程")); }

    public synchronized RecruitmentRecord executeSecondInvitation(String id, SecondInvitationRequest input) {
        if (!"HR".equals(input.role())) throw blocked(id, input.role(), "只有 HR 可以執行二次邀約。"); RecruitmentRecord current = record(id); requireOpen(current, input.role());
        if (current.candidateRespondedAt != null) throw new BusinessException("求職者已回覆，不可執行二次邀約。");
        if (current.secondInvitationAt != null) throw new BusinessException("此案件已執行二次邀約，不可重複執行。");
        if (current.secondInvitationReminderAt == null || !needsSecondInvitation(current, now())) throw new BusinessException("此案件目前不符合二次邀約條件。");
        String result = text(input.result(), "請填寫二次邀約執行結果。");
        String actor = text(input.actorName(), "缺少操作人。");
        OffsetDateTime now = now(); current.secondInvitationAt = now; current.secondInvitationResult = result;
        String previous = current.status;
        current.status = "SECOND_INVITATION_NO_RESPONSE";
        current.statusHistory.add(new StatusHistory(previous, current.status, input.role(), actor, now, "執行二次邀約：" + result));
        audit("EXECUTE_SECOND_INVITATION", input.role(), "SUCCEEDED", id, result, now, actor); save(); return current;
    }

    public synchronized RecruitmentRecord updateArrangement(String id, ArrangementUpdateRequest input) {
        RecruitmentRecord current = record(id); requireOpen(current, input.role());
        String actor = text(input.actorName(), "缺少操作人。");
        Set<String> allowed = "HR".equals(input.role()) ? HR_FIELDS : "MANAGER".equals(input.role()) ? MANAGER_FIELDS : Set.of();
        if (input.changedFields() == null || input.changedFields().isEmpty()) throw new BusinessException("沒有可儲存的面試安排異動。");
        input.changedFields().stream().filter(field -> !allowed.contains(field)).findFirst().ifPresent(field -> { throw blocked(id, input.role(), "目前角色無權修改欄位：" + field + "。"); });
        List<String> managers = current.interviewManagers;
        String location = current.interviewLocation;
        String notes = current.interviewNotes;
        List<OffsetDateTime> slots = current.expectedTimeSlots;
        OffsetDateTime confirmed = null;
        if (input.changedFields().contains("interviewManagers")) {
            managers = input.interviewManagers() == null ? List.of() : input.interviewManagers().stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
            if (new LinkedHashSet<>(managers).size() != managers.size()) throw new BusinessException("面試主管不可重複。");
            if (managers.size() > 3) throw new BusinessException("同一案件最多三位不同主管；本次變更未儲存。");
        }
        if (input.changedFields().contains("interviewLocation")) {
            location = input.interviewLocation() == null ? "" : input.interviewLocation().trim();
            if (!location.isEmpty() && !Set.of("南港", "板橋").contains(location)) throw new BusinessException("面試地點僅限南港或板橋。");
        }
        if (input.changedFields().contains("interviewNotes")) notes = input.interviewNotes() == null ? "" : input.interviewNotes().trim();
        if (input.changedFields().contains("expectedTimeSlots")) slots = parseDates(input.expectedTimeSlots());
        if (input.changedFields().contains("confirmedInterviewAt")) {
            confirmed = date(input.confirmedInterviewAt(), "請提供有效的最終面試時間。");
            validateConfirmedTime(confirmed, slots, id, input.role());
        }
        current.interviewManagers = new ArrayList<>(managers); current.interviewLocation = location;
        current.interviewNotes = notes; current.expectedTimeSlots = new ArrayList<>(slots);
        OffsetDateTime now = now();
        if (confirmed != null) {
            String old = current.status;
            current.interviewAt = confirmed; current.status = "INTERVIEW_CONFIRMED";
            current.statusHistory.add(new StatusHistory(old, current.status, input.role(), actor, now, "HR 確認最終面試時間"));
        }
        current.arrangementHistory.add(new ArrangementHistory(input.role(), actor, List.copyOf(input.changedFields()), now));
        syncCalendar(current, actor);
        audit("UPDATE_INTERVIEW_ARRANGEMENT", input.role(), "SUCCEEDED", id, null, now, actor); save(); return current;
    }

    public synchronized RecruitmentRecord updateNotificationDetails(String id, NotificationDetailsRequest input) {
        RecruitmentRecord current = notificationGuard(id, input.role(), input.actorName());
        if (current.notificationTask != null) throw notificationBlocked(id, input.role(), "通知已開始執行，不能更改通知內容；改期請使用面試安排。");
        if (input.details() == null) throw notificationBlocked(id, input.role(), "缺少通知資料。");
        current.notificationDetails = input.details();
        audit("UPDATE_NOTIFICATION_DETAILS", input.role(), "SUCCEEDED", id, null, now(), input.actorName());
        save(); return current;
    }
    public synchronized NotificationRenderer.Preview previewNotification(String id, ScheduleRequest input) {
        return NotificationRenderer.preview(notificationGuard(id, input.role(), input.actorName()));
    }
    public synchronized RecruitmentRecord executeNotification(String id, NotificationRequest input) {
        RecruitmentRecord current = notificationGuard(id, input.role(), input.actorName());
        NotificationRenderer.Preview preview = NotificationRenderer.preview(current);
        if (!preview.errors().isEmpty()) throw notificationBlocked(id, input.role(), String.join("\n", preview.errors()));
        NotificationTask task = current.notificationTask;
        if (task == null) {
            task = new NotificationTask(); task.id = "NOTICE-" + UUID.randomUUID();
            task.to = preview.to(); task.cc = preview.cc(); task.html = preview.html();
            current.notificationTask = task;
        }
        if (!"成功".equals(task.emailStatus)) {
            task.to = preview.to(); task.cc = preview.cc(); task.html = preview.html();
            task.emailStatus = input.failEmail() ? "失敗" : "成功";
            task.emailFailure = input.failEmail() ? "Demo 模擬 Email 寄送失敗" : null;
            notificationAttempt(current, "EMAIL", task.emailStatus, task.emailFailure, input.actorName());
            save();
        }
        if ("成功".equals(task.emailStatus) && !"成功".equals(task.calendarStatus)) {
            task.calendarStatus = input.failCalendar() ? "失敗" : "成功";
            task.calendarFailure = input.failCalendar() ? "Demo 模擬日曆建立失敗" : null;
            if (!input.failCalendar()) {
                task.event = new CalendarEvent(); task.event.id = "CAL-" + UUID.randomUUID();
                populateCalendar(current);
            }
            notificationAttempt(current, "CALENDAR", task.calendarStatus, task.calendarFailure, input.actorName());
            save();
        }
        return current;
    }
    public synchronized List<AuditEvent> auditEvents(String role) {
        requireHr(role, "只有 HR 可以查看設定與紀錄。"); return List.copyOf(state.auditEvents);
    }
    private RecruitmentRecord notificationGuard(String id, String role, String actor) {
        try { requireHr(role, "只有 HR 可以操作通知信與模擬日曆。");
            RecruitmentRecord current = record(id); requireOpen(current); text(actor, "缺少操作人。"); return current;
        } catch (BusinessException ex) { throw notificationBlocked(id, role, ex.getMessage()); }
    }
    private BusinessException notificationBlocked(String id, String role, String reason) {
        audit("NOTIFICATION_BLOCKED", role, "BLOCKED", id, reason, now()); save(); return new BusinessException(reason);
    }
    private BusinessException blocked(String id, String role, String reason) {
        audit("OPERATION_BLOCKED", role, "BLOCKED", id, reason, now()); save(); return new BusinessException(reason);
    }
    private void notificationAttempt(RecruitmentRecord current, String item, String result, String reason, String actor) {
        current.notificationTask.attempts.add(new NotificationAttempt(item, result, reason, actor, now()));
        audit("SIMULATE_" + item, "HR", result, current.id, reason, now(), actor);
    }
    private void populateCalendar(RecruitmentRecord current) {
        CalendarEvent event = current.notificationTask.event;
        NotificationDetails details = current.notificationDetails;
        event.hrEmail = details.hrEmail(); event.managers = List.copyOf(current.interviewManagers);
        event.managerEmails = NotificationRenderer.managerEmails(current); event.interviewAt = current.interviewAt;
        event.location = current.interviewLocation; event.meetingUrl = details.meetingUrl(); event.resumeUrl = details.resumeUrl(); event.updatedAt = now();
    }
    private void syncCalendar(RecruitmentRecord current, String actor) {
        NotificationTask task = current.notificationTask;
        if ("REJECTED".equals(current.status)) current.interviewCancelled = true;
        if (task == null || task.event == null || "已取消".equals(task.event.status)) return;
        if ("REJECTED".equals(current.status)) {
            task.event.status = "已取消"; task.event.updatedAt = now();
            notificationAttempt(current, "CALENDAR_CANCEL", "成功", current.rejectionReason, actor);
        } else if (!java.util.Objects.equals(task.event.interviewAt, current.interviewAt)
                || !java.util.Objects.equals(task.event.location, current.interviewLocation)
                || !task.event.managers.equals(current.interviewManagers)) {
            populateCalendar(current);
            notificationAttempt(current, "CALENDAR_UPDATE", "成功", "更新原事件，不重寄 Email", actor);
        }
    }
    public synchronized RecruitmentState reset() { state = seed(); save(); return state; }
    public boolean isClosed(RecruitmentRecord record) { return "REJECTED".equals(record.status) || "INTERVIEW_CONFIRMED".equals(record.status) && !record.interviewCancelled && record.interviewAt != null && record.interviewAt.isBefore(now()); }
    private boolean needsSecondInvitation(RecruitmentRecord record, OffsetDateTime now) { return Duration.between(record.keyinAt, now).compareTo(Duration.ofHours(24)) > 0 && record.candidateRespondedAt == null && record.secondInvitationAt == null && !isClosedAt(record, now); }
    private boolean isClosedAt(RecruitmentRecord record, OffsetDateTime now) { return "REJECTED".equals(record.status) || "INTERVIEW_CONFIRMED".equals(record.status) && !record.interviewCancelled && record.interviewAt != null && record.interviewAt.isBefore(now); }
    private void requireOpen(RecruitmentRecord record, String role) { if (isClosed(record)) throw blocked(record.id, role, "案件已結案，不可修改或重新啟動原案件。"); }
    private void requireOpen(RecruitmentRecord record) { if (isClosed(record)) throw new BusinessException("案件已結案，不可修改或重新啟動原案件。"); }
    private void requireHr(String role, String message) { if (!"HR".equals(role)) throw new BusinessException(message); }
    private String text(String value, String message) { if (value == null || value.isBlank()) throw new BusinessException(message); return value.trim(); }
    private OffsetDateTime date(String value, String message) { try { return OffsetDateTime.parse(text(value, message)); } catch (DateTimeParseException ex) { throw new BusinessException(message); } }
    private void validateConfirmedTime(OffsetDateTime confirmed, List<OffsetDateTime> slots, String id, String role) {
        if (!confirmed.isAfter(now())) throw blocked(id, role, "面試時間必須晚於目前時間，不可選擇過去時間。");
        if (slots.stream().noneMatch(slot -> slot.isEqual(confirmed)))
            throw blocked(id, role, "最終面試時間必須選擇主管提供的期待安排時間。");
    }
    private List<OffsetDateTime> parseDates(List<String> values) { if (values == null) return new ArrayList<>(); List<OffsetDateTime> result = new ArrayList<>(); for (String value : values) result.add(date(value, "期待安排時間必須是可解析且非空的日期時間。")); return result; }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private void save() { repository.save(state); }
    private void audit(String action, String role, String result, String id, String reason, OffsetDateTime at) { audit(action, role, result, id, reason, at, role); }
    private void audit(String action, String role, String result, String id, String reason, OffsetDateTime at, String actor) { state.auditEvents.add(new AuditEvent(action, role, result, id, reason, at, actor)); }
    private RecruitmentRecord baseRecord(Candidate candidate, OffsetDateTime at) { RecruitmentRecord record = new RecruitmentRecord(); record.id = nextId(at); record.candidate = candidate; record.keyinAt = at; record.status = "EMAIL_CONTACT"; return record; }
    private String nextId(OffsetDateTime at) { String prefix = "REC-" + at.toLocalDate().toString().replace("-", "") + "-"; int sequence = state == null ? 1 : state.records.size() + 1; Set<String> ids = new LinkedHashSet<>(); if (state != null) state.records.forEach(record -> ids.add(record.id)); while (ids.contains(prefix + "%03d".formatted(sequence))) sequence++; return prefix + "%03d".formatted(sequence); }

    private RecruitmentState seed() {
        RecruitmentState seeded = new RecruitmentState();
        addSeed(seeded, "REC-20261018-001", "林晨宇", "chenyu.lin@example.invalid", "0911-000-101", "後端工程師", "2026-10-18T09:20:00+08:00", "EMAIL_CONTACT", null, null);
        RecruitmentRecord responded = addSeed(seeded, "REC-20261012-002", "陳雨晴", "yuqing.chen@example.invalid", "0922-000-202", "前端工程師", "2026-10-12T14:05:00+08:00", "TIME_CONFIRMING", null, null); responded.candidateRespondedAt = OffsetDateTime.parse("2026-10-13T10:30:00+08:00");
        RecruitmentRecord rejected = addSeed(seeded, "REC-20260928-003", "張書豪", "shuhao.zhang@example.invalid", "0933-000-303", "測試工程師", "2026-09-28T10:00:00+08:00", "REJECTED", null, null); rejected.rejectionType = "求職者婉拒"; rejected.rejectionReason = "已接受其他工作機會";
        addSeed(seeded, "REC-20260920-004", "黃可欣", "kexin.huang@example.invalid", "0955-000-404", "商業分析師", "2026-09-20T09:15:00+08:00", "INTERVIEW_CONFIRMED", "2026-10-01T14:00:00+08:00", null);
        addSeed(seeded, "REC-20261020-005", "吳品妍", "pinyan.wu@example.invalid", "0966-000-505", "產品設計師", "2026-10-20T10:00:00+08:00", "EMAIL_CONTACT", null, null);
        RecruitmentRecord invited = addSeed(seeded, "REC-20261017-006", "周柏廷", "boting.zhou@example.invalid", "0977-000-606", "DevOps 工程師", "2026-10-17T08:00:00+08:00", "EMAIL_CONTACT", null, null); invited.secondInvitationReminderAt = OffsetDateTime.parse("2026-10-18T10:00:00+08:00"); invited.secondInvitationAt = OffsetDateTime.parse("2026-10-18T10:20:00+08:00"); invited.secondInvitationResult = "已完成二邀，等待求職者回覆"; invited.status = "SECOND_INVITATION_NO_RESPONSE"; invited.statusHistory.add(new StatusHistory("EMAIL_CONTACT", invited.status, "HR", "Demo HR", invited.secondInvitationAt, "執行二次邀約：" + invited.secondInvitationResult));
        RecruitmentRecord notice = addSeed(seeded, "REC-DEMO-NOTICE", "許示範", "demo.candidate@example.invalid", "0900-000-707", "後端工程師", now().minusHours(2).toString(), "INTERVIEW_CONFIRMED", now().plusDays(7).withHour(14).withMinute(0).withSecond(0).withNano(0).toString(), null);
        notice.candidateRespondedAt = now(); notice.interviewManagers.add("Demo 王主管"); notice.interviewLocation = "南港";
        notice.notificationDetails = new NotificationDetails("interview-notice", "許", "先生/小姐", "https://training.example.invalid/preparation", "https://training.example.invalid/meeting", "https://training.example.invalid/resume", "hr@training.example.invalid");
        return seeded;
    }
    private RecruitmentRecord addSeed(RecruitmentState target, String id, String name, String email, String phone, String position, String keyin, String status, String interview, Consumer<RecruitmentRecord> customizer) { RecruitmentRecord record = new RecruitmentRecord(); record.id=id; record.candidate=new Candidate(name,email,phone,position); record.keyinAt=OffsetDateTime.parse(keyin); record.status=status; record.interviewAt=interview==null?null:OffsetDateTime.parse(interview); record.statusHistory.add(new StatusHistory(null,"EMAIL_CONTACT","HR","Demo HR",record.keyinAt,"建立招募紀錄")); if (!"EMAIL_CONTACT".equals(status)) record.statusHistory.add(new StatusHistory("EMAIL_CONTACT",status,"HR","Demo HR",record.keyinAt.plusDays(1),"種子資料狀態")); if(customizer!=null)customizer.accept(record); target.records.add(record); return record; }

    public record CreateRequest(String role, String actorName, String name, String email, String phone, String position, boolean differentPersonConfirmed) {}
    public record StatusUpdateRequest(String role, String actorName, String newStatus, String interviewAt, String rejectionType, String rejectionReason, String reason) {}
    public record FollowUpRequest(String role, String actorName, boolean confirmed, String reason) {}
    public record ScheduleRequest(String role, String actorName) {}
    public record NotificationDetailsRequest(String role, String actorName, NotificationDetails details) {}
    public record NotificationRequest(String role, String actorName, boolean failEmail, boolean failCalendar) {}
    public record SecondInvitationRequest(String role, String actorName, String result) {}
    public record ArrangementUpdateRequest(String role, String actorName, List<String> changedFields, List<String> interviewManagers, String interviewLocation, String interviewNotes, List<String> expectedTimeSlots, String confirmedInterviewAt) {}
    public record DuplicateMatch(String id, Candidate candidate, OffsetDateTime keyinAt, String status, String matchedBy) {}
    public static final class DuplicateException extends BusinessException {
        public final List<DuplicateMatch> matches;
        public DuplicateException(List<DuplicateMatch> matches) { super("可能重複邀約：請核對既有資料，由 HR 確認同一人或不同人。"); this.matches = matches; }
    }
    public static class BusinessException extends RuntimeException { public BusinessException(String message) { super(message); } }
}
