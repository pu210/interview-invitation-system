package com.hytech.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FixtureContractTests {
    @Test
    void 起始資料包含六個必做P0情境() throws Exception {
        JsonNode root = new ObjectMapper().readTree(getClass().getClassLoader().getResourceAsStream("fixtures/scenarios.json"));
        List<String> ids = new ArrayList<>();
        for (JsonNode item : root.get("scenarios")) {
            if ("P0".equals(item.get("priority").asText()) && item.get("required").asBoolean()) ids.add(item.get("scenarioId").asText());
        }
        assertThat(ids).containsExactly("P0-01", "P0-02", "P0-03", "P0-04", "P0-05", "P0-06");
    }
}
