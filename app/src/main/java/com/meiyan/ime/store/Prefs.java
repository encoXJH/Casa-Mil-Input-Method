package com.meiyan.ime.store;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public final class Prefs {
    public static final String NAME = "meiyan_ime";
    public static final String MEMORY = "memory_zh";
    public static final String MEMORY_EN = "memory_en";
    public static final String FUZZY = "fuzzy_on";
    public static final String THEME = "theme_index";
    public static final String FREQ_COLOR = "freq_color_on";
    public static final String TOUCH_JSON = "touch_json";
    public static final String TOUCH_ON = "touch_on";
    public static final String CLIP = "clip_history";
    public static final String PHRASES = "phrases";
    public static final String USER_WORDS = "user_words";
    public static final String AI_ON = "ai_on";
    public static final String AI_KEY = "ai_key";
    public static final String AI_URL = "ai_url";
    public static final String AI_MODEL = "ai_model";
    public static final String AI_NAMES = "ai_names";
    public static final String AI_PROMPTS = "ai_prompts";
    public static final String AI_DOCS = "ai_docs";
    public static final String LOG = "selfcheck_log";
    public static final String HAPTIC_ON = "haptic_on";
    public static final String HAPTIC_LEVEL = "haptic_level";

    public static final String DEFAULT_URL = "https://api.deepseek.com/chat/completions";
    public static final String DEFAULT_MODEL = "deepseek-flash";
    public static final String DEFAULT_NAMES = "语气|专业|分点|风险";
    public static final String DEFAULT_PROMPTS =
            "请在保持原意与事实不变的前提下，优化这段话的语气，使其自然、礼貌、得体。只输出改写后的正文，不要解释。\n\n"
            + "请把这段话改写得更专业、准确、简洁，保留全部关键信息与数字。只输出改写后的正文，不要解释。\n\n"
            + "请把这段话整理成分点列表，每点一行，保留关键信息。只输出改写后的正文，不要解释。\n\n"
            + "请检查这段话里的风险表述（承诺、绝对化、合规、隐私），在不改变原意的前提下降低风险，并给出改写正文。只输出改写后的正文，不要解释。";
    public static final String DEFAULT_DOCS = "|||";

    private final SharedPreferences sp;
    private final SharedPreferences clipSp;

    public Prefs(Context ctx) {
        sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE);
        clipSp = ctx.getSharedPreferences(NAME + "_clip", Context.MODE_PRIVATE);
    }

    public SharedPreferences raw() {
        return sp;
    }

    public String getString(String k, String def) {
        return sp.getString(k, def);
    }

    public void putString(String k, String v) {
        sp.edit().putString(k, v).apply();
    }

    public boolean getBool(String k, boolean def) {
        return sp.getBoolean(k, def);
    }

    public void putBool(String k, boolean v) {
        sp.edit().putBoolean(k, v).apply();
    }

    public int getInt(String k, int def) {
        return sp.getInt(k, def);
    }

    public void putInt(String k, int v) {
        sp.edit().putInt(k, v).apply();
    }

    public static List<String> splitPipe(String s, int size) {
        ArrayList<String> out = new ArrayList<>();
        if (s == null) s = "";
        int st = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == '|') {
                out.add(s.substring(st, i));
                st = i + 1;
            }
        }
        while (out.size() < size) out.add("");
        while (out.size() > size) out.remove(out.size() - 1);
        return out;
    }

    public static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append('|');
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    public void addClip(String text) {
        if (text == null || text.trim().isEmpty()) return;
        String t = text.trim();
        ArrayList<String> list = new ArrayList<>(splitPipe(clipSp.getString(CLIP, ""), 5));
        list.remove(t);
        list.add(0, t);
        while (list.size() > 5) list.remove(list.size() - 1);
        clipSp.edit().putString(CLIP, join(list)).apply();
    }

    public List<String> clipList() {
        ArrayList<String> out = new ArrayList<>();
        for (String s : splitPipe(clipSp.getString(CLIP, ""), 5)) if (!s.isEmpty()) out.add(s);
        return out;
    }

    public List<String> phraseList() {
        ArrayList<String> out = new ArrayList<>();
        for (String s : splitPipe(getString(PHRASES, "收到|好的|稍等|谢谢"), 200)) if (!s.isEmpty()) out.add(s);
        return out;
    }

    public void savePhrases(List<String> list) {
        putString(PHRASES, join(list));
    }
}