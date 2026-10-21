package com.hytech.training;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class InterviewArrangementApiTests {
    @Autowired MockMvc mockMvc;

    @Test void 主管四欄可通過但越權欄位被後端拒絕() throws Exception {
        mockMvc.perform(post("/api/interview-arrangements/validate-update").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"role":"MANAGER","status":"EMAIL_COMMUNICATING","closed":false,"changedFields":["interviewLocation"],"interviewManagers":["王主管"],"interviewLocation":"南港","expectedTimeSlots":[]}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true));
        mockMvc.perform(post("/api/interview-arrangements/validate-update").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"role":"MANAGER","status":"EMAIL_COMMUNICATING","closed":false,"changedFields":["confirmedInterviewAt"],"interviewManagers":[],"expectedTimeSlots":[],"confirmedInterviewAt":"2026-11-01T10:00:00+08:00"}
                        """))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.errors.confirmedInterviewAt").exists());
    }

    @Test void 第四位主管會被後端拒絕() throws Exception {
        mockMvc.perform(post("/api/interview-arrangements/validate-update").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"role":"MANAGER","status":"TIME_CONFIRMING","closed":false,"changedFields":["interviewManagers","expectedTimeSlots"],"interviewManagers":["甲","乙","丙","丁"],"expectedTimeSlots":["2026-11-01T10:00:00+08:00"]}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors.interviewManagers").exists())
                .andExpect(jsonPath("$.errors.expectedTimeSlots").doesNotExist());
    }
}
