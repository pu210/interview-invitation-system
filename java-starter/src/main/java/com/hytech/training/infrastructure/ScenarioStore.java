package com.hytech.training.infrastructure;

import com.hytech.training.domain.Models.ScenarioState;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public final class ScenarioStore {
    private final Map<String, ScenarioState> states = new ConcurrentHashMap<>();
    public ScenarioState save(ScenarioState state) { states.put(state.scenarioId(), state); return state; }
    public ScenarioState get(String scenarioId) { return states.get(scenarioId); }
}
