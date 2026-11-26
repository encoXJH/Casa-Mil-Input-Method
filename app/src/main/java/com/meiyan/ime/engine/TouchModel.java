package com.meiyan.ime.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class TouchModel {
    public static class Stat {
        public int n;
        public double sx, sy, sxx, syy, sxy;
        public int corrections;
    }

    private final HashMap<Character, Stat> stats = new HashMap<>();
    private boolean enabled = true;

    public synchronized void setEnabled(boolean v) {
        enabled = v;
    }

    public synchronized boolean isEnabled() {
        return enabled;
    }

    public synchronized void record(char key, float nx, float ny) {
        if (!enabled) return;
        Stat s = stats.computeIfAbsent(key, k -> new Stat());
        s.n++;
        s.sx += nx;
        s.sy += ny;
        s.sxx += nx * nx;
        s.syy += ny * ny;
        s.sxy += nx * ny;
    }

    public synchronized float[] offsetOf(char key) {
        Stat s = stats.get(key);
        if (s == null || s.n < 6) return new float[]{0f, 0f};
        float ox = (float) (s.sx / s.n) - 0.5f;
        float oy = (float) (s.sy / s.n) - 0.5f;
        return new float[]{ox, oy};
    }

    public synchronized void noteCorrection(char wrong, char right) {
        if (!enabled) return;
        Stat s = stats.computeIfAbsent(wrong, k -> new Stat());
        s.corrections++;
    }

    private static final String[] ROWS = {"qwertyuiop", "asdfghjkl", "zxcvbnm"};

    public static boolean areNeighbors(char a, char b) {
        if (a == b) return false;
        int ra = -1, ia = -1, rb = -1, ib = -1;
        for (int r = 0; r < ROWS.length; r++) {
            int p = ROWS[r].indexOf(a);
            if (p >= 0) {
                ra = r;
                ia = p;
            }
            p = ROWS[r].indexOf(b);
            if (p >= 0) {
                rb = r;
                ib = p;
            }
        }
        if (ra < 0 || rb < 0) return false;
        if (ra == rb) return Math.abs(ia - ib) == 1;
        return Math.abs(ra - rb) == 1 && Math.abs(ia - ib) <= 1;
    }

    public synchronized void discount(char key) {
        Stat s = stats.get(key);
        if (s == null) return;
        if (s.n > 0) s.n = Math.max(0, s.n - 1);
    }

    public synchronized int countOf(char key) {
        Stat s = stats.get(key);
        return s == null ? 0 : s.n;
    }

    public synchronized int sampleCount() {
        int n = 0;
        for (Stat s : stats.values()) n += s.n;
        return n;
    }

    public synchronized int maxCount() {
        int max = 0;
        for (Stat s : stats.values()) if (s.n > max) max = s.n;
        return max;
    }

    public synchronized double corrOf(char key) {
        Stat s = stats.get(key);
        if (s == null || s.n < 2) return 0;
        double mx = s.sx / s.n;
        double my = s.sy / s.n;
        double vx = s.sxx / s.n - mx * mx;
        double vy = s.syy / s.n - my * my;
        if (vx <= 1e-9 || vy <= 1e-9) return 0;
        double cov = s.sxy / s.n - mx * my;
        return cov / Math.sqrt(vx * vy);
    }

    public synchronized int correctionCount() {
        int n = 0;
        for (Stat s : stats.values()) n += s.corrections;
        return n;
    }

    public synchronized int learnedKeys() {
        int n = 0;
        for (Stat s : stats.values()) if (s.n > 0) n++;
        return n;
    }

    public synchronized void clear() {
        stats.clear();
    }

    public synchronized String exportJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"keys\":{");
        boolean first = true;
        for (Map.Entry<Character, Stat> e : stats.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            Stat s = e.getValue();
            sb.append('"').append(e.getKey()).append("\":{")
                    .append("\"n\":").append(s.n).append(',')
                    .append("\"sx\":").append(s.sx).append(',')
                    .append("\"sy\":").append(s.sy).append(',')
                    .append("\"sxx\":").append(s.sxx).append(',')
                    .append("\"syy\":").append(s.syy).append(',')
                    .append("\"sxy\":").append(s.sxy).append(',')
                    .append("\"corr\":").append(corrOf(e.getKey())).append(',')
                    .append("\"c\":").append(s.corrections).append('}');
        }
        sb.append("},\"enabled\":").append(enabled).append('}');
        return sb.toString();
    }

    public synchronized int importJson(String json) {
        if (json == null) return 0;
        int count = 0;
        int pos = 0;
        int keysAt = json.indexOf("\"keys\"");
        if (keysAt < 0) return 0;
        while (true) {
            int q1 = json.indexOf('"', pos);
            if (q1 < 0) break;
            int q2 = json.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            String key = json.substring(q1 + 1, q2);
            if (key.length() != 1) {
                pos = q2 + 1;
                if (pos <= keysAt) continue;
                int next = json.indexOf(':', q2);
                if (next < 0) break;
                pos = next + 1;
                continue;
            }
            int brace = json.indexOf('{', q2);
            int end = json.indexOf('}', brace);
            if (brace < 0 || end < 0) break;
            String body = json.substring(brace + 1, end);
            Stat s = parseStat(body);
            if (s != null) {
                stats.put(key.charAt(0), s);
                count++;
            }
            pos = end + 1;
            int comma = json.indexOf(',', pos);
            if (comma < 0) break;
            pos = comma + 1;
        }
        return count;
    }

    private static Stat parseStat(String body) {
        Stat s = new Stat();
        String[] parts = body.split(",");
        for (String p : parts) {
            int c = p.indexOf(':');
            if (c < 0) continue;
            String k = p.substring(0, c).replace("\"", "").trim();
            String v = p.substring(c + 1).trim();
            try {
                switch (k) {
                    case "n": s.n = (int) Double.parseDouble(v); break;
                    case "sx": s.sx = Double.parseDouble(v); break;
                    case "sy": s.sy = Double.parseDouble(v); break;
                    case "sxx": s.sxx = Double.parseDouble(v); break;
                    case "syy": s.syy = Double.parseDouble(v); break;
                    case "sxy": s.sxy = Double.parseDouble(v); break;
                    case "c": s.corrections = (int) Double.parseDouble(v); break;
                    default: break;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return s;
    }

    public synchronized String statsText() {
        return "已学键 " + learnedKeys() + " · 样本 " + sampleCount() + " · 纠错 " + correctionCount();
    }

    public synchronized ArrayList<Character> keys() {
        return new ArrayList<>(stats.keySet());
    }
}