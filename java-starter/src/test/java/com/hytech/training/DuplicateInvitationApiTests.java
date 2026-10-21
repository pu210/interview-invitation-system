package com.hytech.training;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hytech.training.application.RecruitmentService;
import com.hytech.training.infrastructure.RecruitmentStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "recruitment.data-file=target/test-data/duplicate-api.json")
@AutoConfigureMockMvc
class DuplicateInvitationApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RecruitmentService service;
    @Autowired RecruitmentStateRepository repository;
    @BeforeEach void reset() { service.reset(); }

    @Test void createDecisionFlowAndPersistence() throws Exception {
        String email = service.record("REC-DEMO-NOTICE").candidate.email();
        mvc.perform(post("/api/recruitment/records").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                Map.of("role","HR","actorName","HR","name","另一姓名","email"," " + email.toUpperCase() + " ","phone","0988111222","position","工程師","differentPersonConfirmed",true))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("30 天內重複邀約")));
        var input = Map.of("role","HR","actorName","HR","name","許示範","email","unique@example.invalid","phone","0988111222","position","工程師");
        int before = repository.load().records.size();
        mvc.perform(post("/api/recruitment/records").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.duplicateMatches[0].candidate.name").value("許示範"));
        mvc.perform(post("/api/recruitment/duplicate-decisions/same-person").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(input)))
                .andExpect(status().isOk());
        assertEquals(before, repository.load().records.size());
        assertTrue(repository.load().auditEvents.stream().anyMatch(e -> e.result().equals("SAME_PERSON")));
        var different = new java.util.HashMap<String,Object>(input);
        different.put("differentPersonConfirmed", true);
        mvc.perform(post("/api/recruitment/records").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(different)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.notificationTask").doesNotExist());
        assertEquals(before + 1, repository.load().records.size());
        assertTrue(repository.load().auditEvents.stream().anyMatch(e -> e.result().equals("DIFFERENT_PERSON")));
    }

    @Test void managerAndDeleteAreRejectedWithoutCreatingBusinessRecords() throws Exception {
        var body = mapper.writeValueAsString(Map.of("role","MANAGER","actorName","主管","name","許示範","email","x@example.invalid","phone","0988111222","position","工程師","differentPersonConfirmed",true));
        String before = mapper.writeValueAsString(repository.load().records);
        for (String endpoint : new String[]{"/records", "/duplicate-decisions/same-person"})
            mvc.perform(post("/api/recruitment" + endpoint).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableEntity());
        mvc.perform(delete("/api/recruitment/records/REC-DEMO-NOTICE")).andExpect(status().isMethodNotAllowed());
        assertEquals(before, mapper.writeValueAsString(repository.load().records));
    }
}
