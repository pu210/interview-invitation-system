package com.hytech.training.web;

import com.hytech.training.application.InterviewArrangementPolicy;
import com.hytech.training.application.InterviewArrangementPolicy.UpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Compatibility endpoint for the starter contract. Real writes use /api/recruitment. */
@RestController
@RequestMapping("/api/interview-arrangements")
public final class InterviewArrangementController {
    private final InterviewArrangementPolicy policy;
    public InterviewArrangementController(InterviewArrangementPolicy policy) { this.policy = policy; }
    @PostMapping("/validate-update")
    public ResponseEntity<?> validateUpdate(@RequestBody UpdateRequest request) {
        var result = policy.validate(request);
        return result.valid() ? ResponseEntity.ok(result) : ResponseEntity.unprocessableEntity().body(result);
    }
}
