package com.meiyan.ime.engine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;

public final class Fuzzy {
    private static final String[][] INITIAL = {
            {"zh", "z"}, {"ch", "c"}, {"sh", "s"}, {"n", "l"}, {"f", "h"}, {"r", "l"}
    };
    private static final String[][] FINAL = {
            {"ang", "an"}, {"eng", "en"}, {"ing", "in"}, {"iang", "ian"},
            {"uang", "uan"}, {"iong", "ion"}, {"ong", "on"}
    };
    private static final HashMap<String, ArrayList<String>> CACHE = new HashMap<>();

    private Fuzzy() {
    }

    public static char normChar(char c) {
        if (c == 'n' || c == 'r') return 'l';
        if (c == 'f') return 'h';
        return c;
    }

    public static String normChars(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) sb.append(normChar(s.charAt(i)));
        return sb.toString();
    }

    public static ArrayList<String> variants(String syl) {
        String key = syl == null ? "" : syl;
        synchronized (CACHE) {
            ArrayList<String> hit = CACHE.get(key);
            if (hit != null) return hit;
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add(key);
        for (String[] p : INITIAL) {
            String a = p[0], b = p[1];
            if (key.startsWith(a)) out.add(b + key.substring(a.length()));
            if (key.startsWith(b)) out.add(a + key.substring(b.length()));
        }
        ArrayList<String> basis = new ArrayList<>(out);
        for (String base : basis) {
            for (String[] p : FINAL) {
                String a = p[0], b = p[1];
                int ia = base.indexOf(a);
                if (ia >= 0) out.add(base.substring(0, ia) + b + base.substring(ia + a.length()));
                int ib = base.indexOf(b);
                if (ib >= 0) out.add(base.substring(0, ib) + a + base.substring(ib + b.length()));
            }
        }
        ArrayList<String> normed = new ArrayList<>(out);
        for (String v : normed) out.add(normChars(v));
        ArrayList<String> result = new ArrayList<>(out);
        synchronized (CACHE) {
            if (CACHE.size() > 8192) CACHE.clear();
            CACHE.put(key, result);
        }
        return result;
    }
}