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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "recruitment.data-file=target/test-data/bug-fix-api.json")
@AutoConfigureMockMvc
class BugFixApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired RecruitmentService service;
    @Autowired RecruitmentStateRepository repository;
    private static final String ID = "REC-20261012-002";
    private static final String BASE = "/api/recruitment/records/" + ID;
    @BeforeEach void reset() { service.reset(); }

    @Test void bothConfirmationApisRejectPastAndUnavailableTimesWithoutPartialWrites() throws Exception {
        var available = OffsetDateTime.now().plusDays(5).withNano(0);
        var past = OffsetDateTime.now().minusMinutes(1).withNano(0);
        mvc.perform(put(BASE + "/arrangement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                Map.of("role","MANAGER","actorName","主管甲","changedFields",List.of("expectedTimeSlots"),
                        "expectedTimeSlots",List.of(available.toString(),past.toString()))))).andExpect(status().isOk());
        String before = mapper.writeValueAsString(repository.load().records);
        for (String time : List.of(past.toString(), available.plusHours(1).toString())) {
            mvc.perform(patch(BASE + "/status").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                    Map.of("role","HR","actorName","HR","newStatus","INTERVIEW_CONFIRMED","interviewAt",time))))
                    .andExpect(status().isUnprocessableEntity());
            mvc.perform(put(BASE + "/arrangement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                    Map.of("role","HR","actorName","HR","changedFields",List.of("interviewLocation","confirmedInterviewAt"),
                            "interviewLocation","板橋","confirmedInterviewAt",time))))
                    .andExpect(status().isUnprocessableEntity());
            assertEquals(before, mapper.writeValueAsString(repository.load().records));
        }
        // The same instant with a different offset must still match the manager's slot.
        String utc = available.toInstant().toString();
        mvc.perform(patch(BASE + "/status").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                Map.of("role","HR","actorName","確認 HR","newStatus","INTERVIEW_CONFIRMED","interviewAt",utc))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INTERVIEW_CONFIRMED"));
        var stored = repository.load().records.stream().filter(r -> r.id.equals(ID)).findFirst().orElseThrow();
        assertEquals(available.toInstant(), stored.interviewAt.toInstant());
        assertEquals("確認 HR", stored.statusHistory.getLast().actorName());
    }

    @Test void noManagerSlotsCannotConfirmAndFollowUpCannotBypass30Days() throws Exception {
        service.record("REC-20260928-003").keyinAt = OffsetDateTime.now().minusDays(2);
        repository.save(service.state());
        String before = mapper.writeValueAsString(repository.load().records);
        mvc.perform(put(BASE + "/arrangement").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(
                Map.of("role","HR","actorName","HR","changedFields",List.of("confirmedInterviewAt"),
                        "confirmedInterviewAt",OffsetDateTime.now().plusDays(1).toString()))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/recruitment/records/REC-20260928-003/follow-up-cases").contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"HR\",\"actorName\":\"HR\",\"confirmed\":true,\"reason\":\"再次面試\"}"))
                .andExpect(status().isUnprocessableEntity());
        assertEquals(before, mapper.writeValueAsString(repository.load().records));
    }
}
