package com.hytech.training.application;

import com.hytech.training.domain.RecruitmentModels.*;
import java.net.URI;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One trusted HTML template. User values are never interpreted as markup. */
public final class NotificationRenderer {
    private NotificationRenderer() {}
    public static final String TEMPLATE_ID = "interview-notice";
    public static final String TEMPLATE = """
            <p>親愛的 <strong>{{姓氏}}{{先生/小姐}}</strong> 您好，</p>
            <p>邀請您參與範例科技的 {{職缺}} 面試。</p>
            <p><strong>面試時間：{{面試時間}}</strong></p>
            <p>面試地點：{{地點}}（Demo 虛構地點）。</p>
            <p>面試前資料：<a href="{{面試前資料}}">填寫資料</a></p>
            <p>範例科技 人資部（模擬通知）</p>
            """;
    public record Preview(List<String> errors, String html, String to, List<String> cc) {}
    public static List<String> managerEmails(RecruitmentRecord record) {
        List<String> emails = new ArrayList<>();
        for (int i = 0; i < record.interviewManagers.size(); i++) emails.add("manager" + (i + 1) + "@training.example.invalid");
        return emails;
    }
    public static Preview preview(RecruitmentRecord record) {
        List<String> errors = new ArrayList<>();
        if (!"INTERVIEW_CONFIRMED".equals(record.status)) errors.add("HR 尚未確認面試。");
        if (record.interviewAt == null || record.interviewCancelled) errors.add("缺少有效面試時間。");
        if (!email(record.candidate.email())) errors.add("求職者 Email 格式無效。");
        required(errors, record.candidate.position(), "應徵職缺");
        if (record.interviewManagers.isEmpty()) errors.add("缺少面試主管。");
        if (!("南港".equals(record.interviewLocation) || "板橋".equals(record.interviewLocation))) errors.add("缺少有效面試地點（南港／板橋）。");
        NotificationDetails details = record.notificationDetails;
        if (details == null) details = new NotificationDetails(null, null, null, null, null, null, null);
        if (!TEMPLATE_ID.equals(details.templateId())) errors.add("HTML 信件範本不存在，請選取面試通知信。");
        required(errors, details.surname(), "姓氏");
        required(errors, details.salutation(), "先生/小姐（稱謂）");
        url(errors, details.preparationUrl(), "面試前資料");
        url(errors, details.meetingUrl(), "會議連結");
        url(errors, details.resumeUrl(), "履歷連結");
        if (!email(details.hrEmail())) errors.add("HR Email 為必填且須為有效 Email。");
        String html = null;
        if (errors.isEmpty()) {
            String time = record.interviewAt.atZoneSameInstant(ZoneId.of("Asia/Taipei"))
                    .format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm 'Asia/Taipei'"));
            Map<String, String> values = Map.of("姓氏", details.surname(), "先生/小姐", details.salutation(),
                    "面試時間", time, "面試前資料", details.preparationUrl(), "職缺", record.candidate.position(), "地點", record.interviewLocation);
            Matcher variables = Pattern.compile("\\{\\{([^{}]+)}}").matcher(TEMPLATE);
            html = variables.replaceAll(match -> Matcher.quoteReplacement(escape(values.getOrDefault(match.group(1), "{{" + match.group(1) + "}}"))));
            if (html.contains("{{") || html.contains("}}")) { errors.add("信件含未替換變數。"); html = null; }
        }
        return new Preview(errors, html, record.candidate.email(), managerEmails(record));
    }
    public static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private static void required(List<String> errors, String value, String name) {
        if (value == null || value.isBlank()) errors.add("缺少" + name + "。");
    }
    private static boolean email(String value) { return value != null && value.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"); }
    private static void url(List<String> errors, String value, String name) {
        try {
            URI uri = URI.create(value == null ? "" : value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) { errors.add(name + "須為有效的 http／https 網址。"); }
    }
}
