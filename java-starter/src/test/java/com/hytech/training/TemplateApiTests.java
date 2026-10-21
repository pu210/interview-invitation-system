package com.hytech.training;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hytech.training.application.RecruitmentService;
import com.hytech.training.infrastructure.RecruitmentStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "recruitment.data-file=target/test-data/template-api.json")
@AutoConfigureMockMvc
class TemplateApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Value("${recruitment.data-file}") String dataFile;
    private static final String BASE = "/api/recruitment/records/REC-DEMO-NOTICE";
    private final Map<String, String> hr = Map.of("role", "HR", "actorName", "範本驗收 HR");

    @BeforeEach void reset() throws Exception { call(post("/api/recruitment/demo/reset"), hr, 200); }
    private JsonNode call(MockHttpServletRequestBuilder request, Object body, int expected) throws Exception {
        return mapper.readTree(mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private JsonNode current() throws Exception {
        return mapper.readTree(mvc.perform(get(BASE)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
    private ObjectNode details() throws Exception { return (ObjectNode) current().path("notificationDetails").deepCopy(); }
    private void save(ObjectNode details) throws Exception {
        call(put(BASE + "/notification-details"), Map.of("role", "HR", "actorName", "範本驗收 HR", "details", details), 200);
    }

    @Test void catalogSelectionPreviewAndServiceRestartUseSavedInputs() throws Exception {
        JsonNode catalog = mapper.readTree(mvc.perform(get("/api/recruitment/notification-templates?role=HR"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertEquals(1, catalog.size());
        String template = catalog.get(0).path("html").asText();
        for (String variable : new String[]{"姓氏", "先生/小姐", "面試時間", "面試前資料"}) assertTrue(template.contains("{{" + variable + "}}"));
        ObjectNode fields = details();
        fields.put("templateId", catalog.get(0).path("id").asText()).put("surname", "陳<&\"'>")
                .put("salutation", "小姐").put("preparationUrl", "https://training.example.invalid/form?a='&b=1");
        save(fields);
        JsonNode before = current();
        JsonNode preview = call(post(BASE + "/notification/preview"), hr, 200);
        assertTrue(preview.path("errors").isEmpty());
        String html = preview.path("html").asText();
        assertTrue(html.contains("陳&lt;&amp;&quot;&#39;&gt;小姐"));
        assertTrue(html.contains("href=\"https://training.example.invalid/form?a=&#39;&amp;b=1\""));
        assertTrue(html.contains("Asia/Taipei"));
        assertFalse(html.contains("{{"));
        assertEquals(before, current(), "預覽不應建立通知或日曆");
        var restarted = new RecruitmentService(new RecruitmentStateRepository(mapper, dataFile));
        var restored = restarted.record("REC-DEMO-NOTICE");
        assertEquals("陳<&\"'>", restored.notificationDetails.surname());
        assertNull(restored.notificationTask);
        assertEquals(html, restarted.previewNotification(restored.id, new RecruitmentService.ScheduleRequest("HR", "重新啟動驗收")).html());
        JsonNode executed = call(post(BASE + "/notification/execute"), hr, 200);
        assertEquals(html, executed.path("notificationTask").path("html").asText());
    }

    @ParameterizedTest
    @ValueSource(strings = {"surname", "salutation", "preparationUrl", "templateId"})
    void eachRequiredValueBlocksPreviewAndSendWithoutSideEffects(String field) throws Exception {
        ObjectNode fields = details(); fields.put(field, " "); save(fields);
        JsonNode before = current();
        JsonNode preview = call(post(BASE + "/notification/preview"), hr, 200);
        assertFalse(preview.path("errors").isEmpty()); assertTrue(preview.path("html").isNull());
        call(post(BASE + "/notification/execute"), hr, 422);
        assertEquals(before, current());
        var disk = new RecruitmentStateRepository(mapper, dataFile).load();
        assertNull(disk.records.stream().filter(r -> r.id.equals("REC-DEMO-NOTICE")).findFirst().orElseThrow().notificationTask);
        assertTrue(disk.auditEvents.stream().anyMatch(e -> e.action().equals("NOTIFICATION_BLOCKED")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html,<script>alert(1)</script>", "file:///demo", "//example.invalid/form", "https://user:password@example.invalid", "https://example.invalid/\" onclick=\"alert(1)", "https://example.invalid/\nscript"})
    void unsafePreparationUrlsAreRejected(String url) throws Exception {
        ObjectNode fields = details(); fields.put("preparationUrl", url); save(fields);
        JsonNode preview = call(post(BASE + "/notification/preview"), hr, 200);
        assertTrue(preview.path("html").isNull()); assertFalse(preview.path("errors").isEmpty());
        call(post(BASE + "/notification/execute"), hr, 422);
        assertTrue(current().path("notificationTask").isNull());
    }

    @Test void unknownTemplateAndInjectedVariableAreRejectedAndManagerCannotAccess() throws Exception {
        ObjectNode fields = details(); fields.put("templateId", "unknown-template"); save(fields);
        assertTrue(call(post(BASE + "/notification/preview"), hr, 200).path("html").isNull());
        call(post(BASE + "/notification/execute"), hr, 422);
        fields.put("templateId", "interview-notice").put("salutation", "{{未知變數}}"); save(fields);
        assertTrue(call(post(BASE + "/notification/preview"), hr, 200).path("html").isNull());
        call(post(BASE + "/notification/execute"), hr, 422);
        mvc.perform(get("/api/recruitment/notification-templates?role=MANAGER")).andExpect(status().isUnprocessableEntity());
        call(post(BASE + "/notification/preview"), Map.of("role", "MANAGER", "actorName", "主管"), 422);
        assertTrue(current().path("notificationTask").isNull());
    }
}
