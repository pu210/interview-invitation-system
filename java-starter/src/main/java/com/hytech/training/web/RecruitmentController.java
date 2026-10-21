package com.hytech.training.web;

import com.hytech.training.application.RecruitmentService;
import com.hytech.training.application.RecruitmentService.*;
import com.hytech.training.domain.RecruitmentModels.RecruitmentRecord;
import com.hytech.training.domain.RecruitmentModels.RecruitmentState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.List;
import com.hytech.training.application.NotificationRenderer;

@RestController
@RequestMapping("/api/recruitment")
public final class RecruitmentController {
    private final RecruitmentService service;
    public RecruitmentController(RecruitmentService service) { this.service = service; }

    @GetMapping
    public RecruitmentState state() { return service.state(); }

    @GetMapping("/records/{id}")
    public RecruitmentRecord record(@PathVariable String id) { return service.record(id); }

    @PostMapping("/records")
    @ResponseStatus(HttpStatus.CREATED)
    public RecruitmentRecord create(@RequestBody CreateRequest request) { return service.create(request); }

    @PatchMapping("/records/{id}/status")
    public RecruitmentRecord updateStatus(@PathVariable String id, @RequestBody StatusUpdateRequest request) {
        return service.updateStatus(id, request);
    }

    @PostMapping("/records/{id}/follow-up-cases")
    @ResponseStatus(HttpStatus.CREATED)
    public RecruitmentRecord followUp(@PathVariable String id, @RequestBody FollowUpRequest request) {
        return service.createFollowUp(id, request);
    }

    @PostMapping("/second-invitation-schedule/run")
    public Object runSchedule(@RequestBody ScheduleRequest request) { return service.runSchedule(request); }

    @PostMapping("/records/{id}/second-invitation")
    public RecruitmentRecord secondInvitation(@PathVariable String id, @RequestBody SecondInvitationRequest request) {
        return service.executeSecondInvitation(id, request);
    }

    @PutMapping("/records/{id}/arrangement")
    public RecruitmentRecord arrangement(@PathVariable String id, @RequestBody ArrangementUpdateRequest request) {
        return service.updateArrangement(id, request);
    }

    @PostMapping("/demo/reset")
    public RecruitmentState reset(@RequestBody ScheduleRequest request) {
        if (!"HR".equals(request.role())) throw new BusinessException("只有 HR 可以重置示範資料。");
        return service.reset();
    }

    @PutMapping("/records/{id}/notification-details")
    public RecruitmentRecord notificationDetails(@PathVariable String id, @RequestBody NotificationDetailsRequest request) {
        return service.updateNotificationDetails(id, request);
    }
    @PostMapping("/records/{id}/notification/preview")
    public Object preview(@PathVariable String id, @RequestBody ScheduleRequest request) {
        return service.previewNotification(id, request);
    }
    @PostMapping("/records/{id}/notification/execute")
    public RecruitmentRecord notify(@PathVariable String id, @RequestBody NotificationRequest request) {
        return service.executeNotification(id, request);
    }
    @GetMapping("/audit-events")
    public Object audit(@RequestParam String role) { return service.auditEvents(role); }
    @GetMapping("/notification-templates")
    public Object templates(@RequestParam String role) {
        if (!"HR".equals(role)) throw new BusinessException("只有 HR 可以存取信件範本。");
        return List.of(Map.of("id", NotificationRenderer.TEMPLATE_ID, "name", "面試通知信（模擬）", "html", NotificationRenderer.TEMPLATE));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> businessError(BusinessException error) {
        return ResponseEntity.unprocessableEntity().body(Map.of("message", error.getMessage()));
    }
}
