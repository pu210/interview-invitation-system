package com.hytech.training.web;

import com.hytech.training.application.ScenarioService;
import com.hytech.training.domain.Models.ScenarioDefinition;
import com.hytech.training.domain.Models.ScenarioState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/scenarios")
public final class ScenarioController {
    private final ScenarioService service;
    public ScenarioController(ScenarioService service) { this.service = service; }
    @GetMapping public List<ScenarioDefinition> list() { return service.list(); }
    @PostMapping("/{id}/reset") public ResponseEntity<?> reset(@PathVariable String id) { return response(service.reset(id)); }
    @PostMapping("/{id}/run") public ResponseEntity<?> run(@PathVariable String id) { return response(service.run(id)); }
    @GetMapping("/{id}/state") public ResponseEntity<?> state(@PathVariable String id) { return response(service.state(id)); }
    private ResponseEntity<?> response(ScenarioState state) { return state == null ? ResponseEntity.status(404).body(Map.of("errorCode", "SCENARIO_NOT_FOUND")) : ResponseEntity.ok(state); }
}
