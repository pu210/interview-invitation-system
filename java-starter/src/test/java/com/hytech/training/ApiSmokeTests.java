package com.hytech.training;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTests {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void 共用情境API可載入重設查詢與執行() throws Exception {
        mockMvc.perform(get("/api/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(10))
                .andExpect(jsonPath("$[0].scenarioId").value("P0-01"));

        mockMvc.perform(post("/api/scenarios/P0-01/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"));

        mockMvc.perform(get("/api/scenarios/P0-01/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioId").value("P0-01"));

        mockMvc.perform(post("/api/scenarios/P0-01/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.nextActions[0]").value("IMPLEMENT_P0_RULE"));
    }
}
