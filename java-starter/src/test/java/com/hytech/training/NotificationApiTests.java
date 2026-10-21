package com.hytech.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hytech.training.infrastructure.RecruitmentStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.OffsetDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "recruitment.data-file=target/test-data/notification-api.json")
@AutoConfigureMockMvc
class NotificationApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Value("${recruitment.data-file}") String dataFile;
    private static final String BASE = "/api/recruitment/records/REC-DEMO-NOTICE";
    private final Map<String, String> hr = Map.of("role", "HR", "actorName", "測試 HR");
    @BeforeEach void reset() throws Exception { request(post("/api/recruitment/demo/reset"), hr, 200); }
    private JsonNode request(MockHttpServletRequestBuilder builder, Object body, int status) throws Exception {
        String response = mvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().is(status)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return mapper.readTree(response);
    }
    private JsonNode execute(boolean email, boolean calendar) throws Exception {
        return request(post(BASE + "/notification/execute"), Map.of("role", "HR", "actorName", "測試 HR", "failEmail", email, "failCalendar", calendar), 200);
    }
    private JsonNode current() throws Exception { return mapper.readTree(mvc.perform(get(BASE)).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)); }
    private JsonNode details() throws Exception { return current().path("notificationDetails").deepCopy(); }
    private void saveDetails(JsonNode details) throws Exception { request(put(BASE + "/notification-details"), Map.of("role", "HR", "actorName", "測試 HR", "details", details), 200); }

    @Test void orderedFailuresRetriesDeduplicationAndDiskReload() throws Exception {
        JsonNode failed = execute(true, false).path("notificationTask");
        assertEquals("失敗", failed.path("emailStatus").asText());
        assertEquals("未執行", failed.path("calendarStatus").asText());
        assertTrue(failed.path("event").isNull());
        assertEquals(1, failed.path("attempts").size());
        var repository = new RecruitmentStateRepository(mapper, dataFile);
        assertEquals("失敗", repository.load().records.stream().filter(r -> r.id.equals("REC-DEMO-NOTICE")).findFirst().orElseThrow().notificationTask.emailStatus);
        JsonNode calendarFailed = execute(false, true).path("notificationTask");
        assertEquals("成功", calendarFailed.path("emailStatus").asText());
        assertEquals("失敗", calendarFailed.path("calendarStatus").asText());
        assertFalse(calendarFailed.path("calendarFailure").asText().isBlank());
        JsonNode complete = execute(true, false).path("notificationTask");
        assertEquals("成功", complete.path("emailStatus").asText());
        assertEquals("成功", complete.path("calendarStatus").asText());
        assertEquals(failed.path("id"), complete.path("id"));
        assertEquals(4, complete.path("attempts").size());
        assertEquals(complete, execute(true, true).path("notificationTask"));
        var loaded = new RecruitmentStateRepository(mapper, dataFile).load();
        var stored = loaded.records.stream().filter(r -> r.id.equals("REC-DEMO-NOTICE")).findFirst().orElseThrow();
        assertEquals(mapper.valueToTree(mapper.treeToValue(complete, com.hytech.training.domain.RecruitmentModels.NotificationTask.class)), mapper.valueToTree(stored.notificationTask));
        assertEquals("hr@training.example.invalid", stored.notificationTask.event.hrEmail);
        assertFalse(stored.notificationTask.event.resumeUrl.isBlank());
    }

    @Test void missingFieldsTemplateAndUnsafeUrlsBlockBothOperations() throws Exception {
        var fields = (com.fasterxml.jackson.databind.node.ObjectNode) details();
        fields.put("templateId", "missing").put("surname", " ").put("salutation", "").put("meetingUrl", "javascript:alert(1)")
                .put("preparationUrl", "").put("resumeUrl", "file:///tmp/resume").put("hrEmail", "invalid");
        saveDetails(fields);
        JsonNode preview = request(post(BASE + "/notification/preview"), hr, 200);
        assertEquals(7, preview.path("errors").size());
        assertTrue(preview.path("html").isNull());
        JsonNode before = current();
        request(post(BASE + "/notification/execute"), hr, 422);
        assertEquals(before, current());
        assertTrue(current().path("notificationTask").isNull());
    }

    @Test void missingArrangementAndUnconfirmedStatusShowAllErrors() throws Exception {
        JsonNode result = request(post("/api/recruitment/records/REC-20261018-001/notification/preview"), hr, 200);
        assertTrue(result.path("errors").size() >= 10);
        request(post("/api/recruitment/records/REC-20261018-001/notification/execute"), hr, 422);
    }

    @Test void previewEscapesTextAndHtmlAttributes() throws Exception {
        var fields = (com.fasterxml.jackson.databind.node.ObjectNode) details();
        fields.put("surname", "<&\"'>").put("preparationUrl", "https://example.invalid/?x='&y=1"); saveDetails(fields);
        String html = request(post(BASE + "/notification/preview"), hr, 200).path("html").asText();
        assertTrue(html.contains("&lt;&amp;&quot;&#39;&gt;"));
        assertTrue(html.contains("?x=&#39;&amp;y=1")); assertFalse(html.contains("{{"));
        assertFalse(html.contains("<script"));
        fields.put("surname", "{{面試時間}}"); saveDetails(fields);
        assertTrue(request(post(BASE + "/notification/preview"), hr, 200).path("html").isNull());
        request(post(BASE + "/notification/execute"), hr, 422);
    }

    @Test void rescheduleAndCancelUseOriginalCalendarWithoutEmailResend() throws Exception {
        JsonNode original = execute(false, false).path("notificationTask");
        String time = OffsetDateTime.now().plusDays(12).withNano(0).toString();
        JsonNode changed = request(put(BASE + "/arrangement"), Map.of("role", "HR", "actorName", "測試 HR", "changedFields", new String[]{"confirmedInterviewAt"}, "confirmedInterviewAt", time), 200);
        assertEquals(original.path("event").path("id"), changed.path("notificationTask").path("event").path("id"));
        assertEquals(original.path("html"), changed.path("notificationTask").path("html"));
        assertEquals(3, changed.path("notificationTask").path("attempts").size());
        assertEquals(OffsetDateTime.parse(time).toInstant(), OffsetDateTime.parse(changed.path("notificationTask").path("event").path("interviewAt").asText()).toInstant());
        JsonNode before = current();
        request(patch(BASE + "/status"), Map.of("role", "HR", "actorName", "HR", "newStatus", "REJECTED", "rejectionType", "求職者婉拒"), 422);
        assertEquals(before, current());
        JsonNode cancelled = request(patch(BASE + "/status"), Map.of("role", "HR", "actorName", "HR", "newStatus", "REJECTED", "rejectionType", "求職者婉拒", "rejectionReason", "時間無法配合"), 200);
        assertEquals("已取消", cancelled.path("notificationTask").path("event").path("status").asText());
        assertEquals(original.path("event").path("id"), cancelled.path("notificationTask").path("event").path("id"));
        request(post(BASE + "/notification/execute"), hr, 422);
        assertEquals(cancelled, current());
    }

    @Test void managerClosedCaseAndMissingActorCannotProduceSideEffects() throws Exception {
        JsonNode before = current();
        var manager = Map.of("role", "MANAGER", "actorName", "主管");
        request(post(BASE + "/notification/execute"), manager, 422);
        request(post(BASE + "/notification/preview"), manager, 422);
        request(put(BASE + "/notification-details"), Map.of("role", "MANAGER", "actorName", "主管", "details", details()), 422);
        request(post(BASE + "/notification/execute"), Map.of("role", "HR"), 422);
        mvc.perform(get("/api/recruitment/notification-templates?role=MANAGER")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/recruitment/audit-events?role=MANAGER")).andExpect(status().isUnprocessableEntity());
        assertEquals(before, current());
        request(post("/api/recruitment/records/REC-20260920-004/notification/execute"), hr, 422);
    }

    @Test void successfulTaskDetailsAreFrozenAndLegacyJsonLoads() throws Exception {
        execute(false, false); JsonNode before = current();
        request(put(BASE + "/notification-details"), Map.of("role", "HR", "actorName", "HR", "details", details()), 422);
        assertEquals(before, current());
        var legacy = mapper.readValue("{\"records\":[{\"id\":\"old\"}],\"schemaVersion\":1}", com.hytech.training.domain.RecruitmentModels.RecruitmentState.class);
        assertNull(legacy.records.getFirst().notificationTask);
        assertEquals(1, legacy.schemaVersion);
    }
}
