package com.hytech.training.web;

import com.hytech.training.application.RecruitmentService;
import com.hytech.training.application.RecruitmentService.*;
import com.hytech.training.domain.RecruitmentModels.RecruitmentRecord;
import com.hytech.training.domain.RecruitmentModels.RecruitmentState;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<?> businessError(BusinessException error) {
        return ResponseEntity.unprocessableEntity().body(Map.of("message", error.getMessage()));
    }
}
