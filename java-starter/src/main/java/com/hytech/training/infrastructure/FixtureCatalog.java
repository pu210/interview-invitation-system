package com.hytech.training.infrastructure;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hytech.training.domain.Models.Candidate;
import com.hytech.training.domain.Models.ExpectedResult;
import com.hytech.training.domain.Models.ScenarioDefinition;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public final class FixtureCatalog {
    private final Map<String, Candidate> candidates;
    private final Map<String, ScenarioDefinition> scenarios;
    private final Map<String, ExpectedResult> expected;

    public FixtureCatalog() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CandidateDocument candidateDocument = read(mapper, "fixtures/candidates.json", CandidateDocument.class);
        ScenarioDocument scenarioDocument = read(mapper, "fixtures/scenarios.json", ScenarioDocument.class);
        ExpectedDocument expectedDocument = read(mapper, "fixtures/expected-results.json", ExpectedDocument.class);
        candidates = candidateDocument.candidates().stream().collect(Collectors.toUnmodifiableMap(Candidate::candidateId, Function.identity()));
        scenarios = scenarioDocument.scenarios().stream().collect(Collectors.toUnmodifiableMap(ScenarioDefinition::scenarioId, Function.identity()));
        expected = expectedDocument.expectedResults().stream().collect(Collectors.toUnmodifiableMap(ExpectedResult::scenarioId, Function.identity()));
    }

    public List<ScenarioDefinition> scenarios() { return scenarios.values().stream().sorted(Comparator.comparing(ScenarioDefinition::scenarioId)).toList(); }
    public Candidate candidate(String id) { return candidates.get(id); }
    public ScenarioDefinition scenario(String id) { return scenarios.get(id); }
    public ExpectedResult expected(String id) { return expected.get(id); }

    private static <T> T read(ObjectMapper mapper, String path, Class<T> type) {
        try (var input = new ClassPathResource(path).getInputStream()) {
            return mapper.readValue(input, type);
        }
        catch (IOException error) { throw new IllegalStateException("無法讀取 fixture：" + path, error); }
    }

    private record CandidateDocument(List<Candidate> candidates) {}
    private record ScenarioDocument(List<ScenarioDefinition> scenarios) {}
    private record ExpectedDocument(List<ExpectedResult> expectedResults) {}
}
