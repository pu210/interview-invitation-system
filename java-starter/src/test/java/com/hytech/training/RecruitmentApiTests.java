package com.hytech.training;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class RecruitmentApiTests {
    @Autowired MockMvc mockMvc;

    @BeforeEach void reset() throws Exception {
        mockMvc.perform(post("/api/recruitment/demo/reset").contentType(MediaType.APPLICATION_JSON)
                .content("""
{"role":"HR","actorName":"測試 HR"}
"""))
                .andExpect(status().isOk());
    }

    @Test void 狀態異動由後端保存且主管不可越權() throws Exception {
        mockMvc.perform(patch("/api/recruitment/records/REC-20261018-001/status").contentType(MediaType.APPLICATION_JSON)
                .content("""
{"role":"MANAGER","actorName":"主管","newStatus":"TIME_CONFIRMING"}
"""))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(patch("/api/recruitment/records/REC-20261018-001/status").contentType(MediaType.APPLICATION_JSON)
                .content("""
{"role":"HR","actorName":"測試 HR","newStatus":"TIME_CONFIRMING","reason":"開始安排"}
"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TIME_CONFIRMING"))
                .andExpect(jsonPath("$.statusHistory.length()").value(2));
        mockMvc.perform(get("/api/recruitment/records/REC-20261018-001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("TIME_CONFIRMING"));
    }

    @Test void 結案案件不能重啟但HR可另立新案() throws Exception {
        mockMvc.perform(patch("/api/recruitment/records/REC-20260928-003/status").contentType(MediaType.APPLICATION_JSON)
                .content("""
{"role":"HR","actorName":"測試 HR","newStatus":"EMAIL_CONTACT"}
"""))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/recruitment/records/REC-20260928-003/follow-up-cases").contentType(MediaType.APPLICATION_JSON)
                .content("""
{"role":"HR","actorName":"測試 HR","confirmed":true,"reason":"新職缺再次邀約"}
"""))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.linkedOriginalCaseId").value("REC-20260928-003"))
                .andExpect(jsonPath("$.status").value("EMAIL_CONTACT"));
    }

    @Test void 二邀排程去重且主管不能執行二邀() throws Exception {
        mockMvc.perform(post("/api/recruitment/second-invitation-schedule/run").contentType(MediaType.APPLICATION_JSON)
                .content("""
{"role":"HR","actorName":"測試 HR"}
"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(1));
        mockMvc.perform(post("/api/recruitment/second-invitation-schedule/run").contentType(MediaType.APPLICATION_JSON)
                .content("""
{"role":"HR","actorName":"測試 HR"}
"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdCount").value(0));
        mockMvc.perform(post("/api/recruitment/records/REC-20261018-001/second-invitation").contentType(MediaType.APPLICATION_JSON)
                .content("""
{"role":"MANAGER","actorName":"主管","result":"越權"}
"""))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(post("/api/recruitment/records/REC-20261018-001/second-invitation").contentType(MediaType.APPLICATION_JSON)
                .content("""
{"role":"HR","actorName":"測試 HR","result":"電話二邀完成"}
"""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.secondInvitationResult").value("電話二邀完成"));
    }

    @Test void 面試安排越權或第四位主管皆整筆拒絕() throws Exception {
        mockMvc.perform(put("/api/recruitment/records/REC-20261018-001/arrangement").contentType(MediaType.APPLICATION_JSON)
                .content("""
{"role":"MANAGER","actorName":"主管","changedFields":["interviewManagers","interviewLocation"],"interviewManagers":["甲","乙","丙","丁"],"interviewLocation":"板橋"}
"""))
                .andExpect(status().isUnprocessableEntity());
        mockMvc.perform(get("/api/recruitment/records/REC-20261018-001"))
                .andExpect(jsonPath("$.interviewManagers.length()").value(0)).andExpect(jsonPath("$.interviewLocation").value(""));
        mockMvc.perform(put("/api/recruitment/records/REC-20261018-001/arrangement").contentType(MediaType.APPLICATION_JSON)
                .content("""
{"role":"MANAGER","actorName":"主管","changedFields":["confirmedInterviewAt"],"confirmedInterviewAt":"2026-11-01T10:00:00+08:00"}
"""))
                .andExpect(status().isUnprocessableEntity());
    }
}
