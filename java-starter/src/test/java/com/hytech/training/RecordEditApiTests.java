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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "recruitment.data-file=target/test-data/record-edit-api.json")
@AutoConfigureMockMvc
class RecordEditApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RecruitmentService service;
    @Autowired RecruitmentStateRepository repository;
    private static final String ID = "REC-20261012-002";
    @BeforeEach void reset() { service.reset(); }
    private Map<String, Object> input() {
        return new HashMap<>(Map.of("role", "HR", "actorName", "測試 HR", "name", " 編輯測試 ",
                "email", " EDIT@Example.invalid ", "phone", "0912-345-678", "position", " 資深工程師 "));
    }
    private void update(String id, Map<String, Object> body, boolean ok) throws Exception {
        mvc.perform(put("/api/recruitment/records/" + id).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body))).andExpect(ok ? status().isOk() : status().isUnprocessableEntity());
    }
    @Test void hrEditsAndReloadsSameJsonWithoutChangingWorkflow() throws Exception {
        var original = new RecruitmentService(repository).record(ID);
        var keyin = original.keyinAt;
        var history = mapper.writeValueAsString(original.statusHistory);
        update(ID, input(), true);
        var restored = new RecruitmentService(repository).record(ID);
        assertEquals("編輯測試", restored.candidate.name());
        assertEquals("edit@example.invalid", restored.candidate.email());
        assertEquals("0912-345-678", restored.candidate.phone());
        assertEquals("資深工程師", restored.candidate.position());
        assertEquals(keyin, restored.keyinAt);
        assertEquals("TIME_CONFIRMING", restored.status);
        assertEquals(history, mapper.writeValueAsString(restored.statusHistory));
        for (String role : List.of("HR", "MANAGER"))
            mvc.perform(get("/api/recruitment?role=" + role)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.records[?(@.id == '" + ID + "')].candidate.name").value("編輯測試"));
        assertTrue(repository.load().auditEvents.stream().anyMatch(e -> "UPDATE_RECORD".equals(e.action())
                && "測試 HR".equals(e.actorName()) && ID.equals(e.recordId())));
    }
    @Test void rejectsUnauthorizedClosedAndProtectedFieldsWithoutChangingRecords() throws Exception {
        String before = mapper.writeValueAsString(repository.load().records);
        for (String role : List.of("MANAGER", "UNKNOWN")) {
            var body = input(); body.put("role", role); update(ID, body, false);
        }
        for (String id : List.of("REC-20260928-003", "REC-20260920-004")) update(id, input(), false);
        for (String field : List.of("status", "keyinAt", "candidateRespondedAt", "notificationTask", "interviewAt")) {
            var body = input(); body.put(field, "forbidden"); update(ID, body, false);
        }
        assertEquals(before, mapper.writeValueAsString(repository.load().records));
    }
    @Test void rejectsInvalidAndUnchangedDataAtomically() throws Exception {
        String before = mapper.writeValueAsString(repository.load().records);
        for (String field : List.of("name", "email", "phone", "position", "actorName")) {
            var body = input(); body.put(field, " "); update(ID, body, false);
            body.remove(field); update(ID, body, false);
        }
        for (var invalid : List.of(Map.entry("email", "invalid"), Map.entry("phone", "123"))) {
            var body = input(); body.put(invalid.getKey(), invalid.getValue()); update(ID, body, false);
        }
        var candidate = service.record(ID).candidate;
        update(ID, new HashMap<>(Map.of("role", "HR", "actorName", "HR", "name", candidate.name(),
                "email", candidate.email(), "phone", candidate.phone(), "position", candidate.position())), false);
        assertEquals(before, mapper.writeValueAsString(repository.load().records));
    }
}
