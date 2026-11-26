package com.meiyan.ime.ai;

import android.content.Context;

import com.meiyan.ime.store.Prefs;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class DeepSeekClient {
    public static class Result {
        public boolean ok;
        public String text;
        public String error;
    }

    private DeepSeekClient() {
    }

    public static Result rewriteText(Context ctx, String prompt, String source, String doc) {
        return rewriteText(ctx, prompt, source, doc, false);
    }

    public static Result rewriteText(Context ctx, String prompt, String source, String doc, boolean force) {
        Prefs p = new Prefs(ctx);
        Result r = new Result();
        if (!force && !p.getBool(Prefs.AI_ON, false)) {
            r.error = "AI 改写未开启，请到设置页开启";
            return r;
        }
        String key = p.getString(Prefs.AI_KEY, "").trim();
        if (key.isEmpty()) {
            r.error = "未填写 API Key，请到设置页填写";
            return r;
        }
        String url = p.getString(Prefs.AI_URL, Prefs.DEFAULT_URL).trim();
        String model = p.getString(Prefs.AI_MODEL, Prefs.DEFAULT_MODEL).trim();
        if (url.isEmpty()) url = Prefs.DEFAULT_URL;
        if (model.isEmpty()) model = Prefs.DEFAULT_MODEL;
        String content = prompt == null ? "" : prompt;
        if (doc != null && !doc.trim().isEmpty()) {
            content = content + "\n\n参考资料：\n" + doc.trim();
        }
        String body = buildBody(model, content, source);
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + key);
            OutputStream os = conn.getOutputStream();
            os.write(body.getBytes(StandardCharsets.UTF_8));
            os.flush();
            os.close();
            int code = conn.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
            String resp = readAll(in);
            if (code < 200 || code >= 300) {
                r.error = "HTTP " + code + " " + trim(resp, 160);
                return r;
            }
            String text = extractContent(resp);
            if (text == null || text.trim().isEmpty()) {
                r.error = "返回为空 " + trim(resp, 160);
                return r;
            }
            r.ok = true;
            r.text = text.trim();
            return r;
        } catch (Exception e) {
            r.error = "网络异常：" + e.getClass().getSimpleName();
            return r;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String buildBody(String model, String prompt, String source) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(esc(model)).append("\",\"temperature\":0.4,\"messages\":[");
        sb.append("{\"role\":\"system\",\"content\":\"").append(esc(prompt)).append("\"},");
        sb.append("{\"role\":\"user\",\"content\":\"").append(esc(source == null ? "" : source)).append("\"}");
        sb.append("]}");
        return sb.toString();
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private static String extractContent(String json) {
        int i = json.indexOf("\"content\"");
        while (i >= 0) {
            int colon = json.indexOf(':', i);
            int q1 = json.indexOf('"', colon + 1);
            if (colon < 0 || q1 < 0) return null;
            StringBuilder sb = new StringBuilder();
            int j = q1 + 1;
            while (j < json.length()) {
                char c = json.charAt(j);
                if (c == '\\' && j + 1 < json.length()) {
                    char n = json.charAt(j + 1);
                    if (n == 'n') sb.append('\n');
                    else if (n == 't') sb.append('\t');
                    else if (n == 'r') sb.append('\r');
                    else if (n == '"') sb.append('"');
                    else if (n == '\\') sb.append('\\');
                    else if (n == 'u' && j + 5 < json.length()) {
                        try {
                            sb.append((char) Integer.parseInt(json.substring(j + 2, j + 6), 16));
                            j += 4;
                        } catch (NumberFormatException ignored) {
                        }
                    } else sb.append(n);
                    j += 2;
                    continue;
                }
                if (c == '"') break;
                sb.append(c);
                j++;
            }
            return sb.toString();
        }
        return null;
    }

    private static String trim(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }
}