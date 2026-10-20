package com.hytech.training.application;

import com.hytech.training.domain.Models.*;
import com.hytech.training.infrastructure.FixtureCatalog;
import com.hytech.training.infrastructure.ScenarioStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public final class ScenarioService {
    private static final Logger LOG = LoggerFactory.getLogger(ScenarioService.class);
    private static final OffsetDateTime FIXED_NOW = OffsetDateTime.parse("2026-09-01T09:00:00+08:00");
    private final FixtureCatalog fixtures;
    private final ScenarioStore store;
    private final WorkflowEngine engine;

    public ScenarioService(FixtureCatalog fixtures, ScenarioStore store, WorkflowEngine engine) {
        this.fixtures = fixtures; this.store = store; this.engine = engine;
    }

    public List<ScenarioDefinition> list() { return fixtures.scenarios(); }
    public ScenarioState reset(String id) {
        ScenarioDefinition scenario = fixtures.scenario(id);
        if (scenario == null || fixtures.candidate(scenario.candidateId()) == null) return null;
        ScenarioState state = new ScenarioState(id, scenario.candidateId(), scenario.operationId(), "READY", List.of(), List.of(), List.of(), List.of(),
                List.of(new AuditEvent("SCENARIO_RESET", "SUCCEEDED", null, FIXED_NOW)));
        LOG.info("scenario_reset scenarioId={} candidateId={} operationId={} status={}", id, scenario.candidateId(), scenario.operationId(), state.status());
        return store.save(state);
    }
    public ScenarioState state(String id) { return store.get(id) == null ? reset(id) : store.get(id); }
    public ScenarioState run(String id) {
        ScenarioDefinition scenario = fixtures.scenario(id);
        if (scenario == null) return null;
        Candidate candidate = fixtures.candidate(scenario.candidateId());
        if (candidate == null) return null;
        ScenarioState result = engine.execute(scenario, candidate, state(id));
        LOG.info("scenario_run scenarioId={} candidateId={} operationId={} status={}", id, candidate.candidateId(), scenario.operationId(), result.status());
        return store.save(result);
    }
}
