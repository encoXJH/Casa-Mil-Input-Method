package com.meiyan.ime.engine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeSet;

public class DictRepo {
    public final LinkedHashMap<String, ArrayList<String>> WORDS = new LinkedHashMap<>();
    public final LinkedHashMap<String, ArrayList<String>> ABBR = new LinkedHashMap<>();
    public final HashMap<String, ArrayList<Character>> SINGLE = new HashMap<>();
    public final LinkedHashMap<String, ArrayList<String>> ASSOC = new LinkedHashMap<>();
    public final LinkedHashSet<String> SYLL = new LinkedHashSet<>();
    public final TreeSet<String> KEYS = new TreeSet<>();
    public final HashMap<Character, HashMap<Integer, ArrayList<String>>> BY_FIRST_LEN = new HashMap<>();
    public final HashMap<Character, String> SINGLE_REV = new HashMap<>();
    public final LinkedHashSet<String> IMPORTED = new LinkedHashSet<>();
    public final LinkedHashSet<String> USER_WORDS = new LinkedHashSet<>();

    private long loadMs;
    private int wordCount;
    private int singleCount;
    private boolean loaded;

    public boolean isLoaded() {
        return loaded;
    }

    public long loadMs() {
        return loadMs;
    }

    public int wordCount() {
        return wordCount;
    }

    public int bucketCount() {
        return WORDS.size();
    }

    public int singleCount() {
        return singleCount;
    }

    public int importedCount() {
        return IMPORTED.size();
    }

    public int userWordCount() {
        return USER_WORDS.size();
    }

    public static boolean isHan(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    public static boolean hasHan(String s) {
        if (s == null) return false;
        for (int i = 0; i < s.length(); i++) if (isHan(s.charAt(i))) return true;
        return false;
    }

    public static boolean isHanWord(String s) {
        if (s == null || s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) if (!isHan(s.charAt(i))) return false;
        return true;
    }

    public static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    public static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static boolean isPunctOnly(String s) {
        if (s == null || s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (isHan(c) || isAsciiLetter(c) || isAsciiDigit(c)) return false;
        }
        return true;
    }

    private static ArrayList<String> readLines(InputStream in) throws IOException {
        ArrayList<String> out = new ArrayList<>();
        if (in == null) return out;
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String s;
        while ((s = br.readLine()) != null) {
            if (s.isEmpty() || s.charAt(0) == '#') continue;
            out.add(s);
        }
        br.close();
        return out;
    }

    private static String[] splitTab(String s) {
        int i = s.indexOf('\t');
        if (i < 0) return new String[]{s};
        return new String[]{s.substring(0, i), s.substring(i + 1)};
    }

    private static ArrayList<String> splitPipe(String s) {
        ArrayList<String> out = new ArrayList<>();
        int st = 0;
        for (int i = 0; i <= s.length(); i++) {
            if (i == s.length() || s.charAt(i) == '|') {
                String t = s.substring(st, i).trim();
                if (!t.isEmpty()) out.add(t);
                st = i + 1;
            }
        }
        return out;
    }

    private static String cleanSyllableText(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 'a' && c <= 'z') sb.append(c);
            else if (c == 'A' && c <= 'Z') sb.append((char) (c + 32));
            else if (c >= 'A' && c <= 'Z') sb.append((char) (c + 32));
        }
        return sb.toString();
    }

    public void load(InputStream wordIn, InputStream abbrIn, InputStream singleIn,
                     InputStream assocIn, InputStream polyIn, InputStream syllIn) throws IOException {
        long t0 = System.currentTimeMillis();

        for (String line : readLines(singleIn)) {
            String[] kv = splitTab(line);
            if (kv.length < 2) continue;
            String syl = kv[0].trim().toLowerCase();
            if (syl.isEmpty()) continue;
            ArrayList<Character> arr = SINGLE.computeIfAbsent(syl, k -> new ArrayList<>());
            for (int i = 0; i < kv[1].length(); i++) {
                char c = kv[1].charAt(i);
                if (isHan(c) && !arr.contains(c)) {
                    arr.add(c);
                    singleCount++;
                    if (!SINGLE_REV.containsKey(c)) SINGLE_REV.put(c, syl);
                }
            }
            SYLL.add(syl);
        }

        for (String line : readLines(polyIn)) {
            String[] kv = splitTab(line);
            if (kv.length < 2) continue;
            String syl = kv[0].trim().toLowerCase();
            ArrayList<Character> arr = SINGLE.computeIfAbsent(syl, k -> new ArrayList<>());
            for (int i = 0; i < kv[1].length(); i++) {
                char c = kv[1].charAt(i);
                if (!isHan(c) || arr.contains(c)) continue;
                arr.add(Math.min(3, arr.size()), c);
            }
            SYLL.add(syl);
        }

        for (String line : readLines(wordIn)) {
            String[] kv = splitTab(line);
            if (kv.length < 2) continue;
            String full = kv[0].trim().toLowerCase();
            if (full.isEmpty()) continue;
            ArrayList<String> arr = WORDS.computeIfAbsent(full, k -> new ArrayList<>());
            for (String w : splitPipe(kv[1])) {
                if (!arr.contains(w)) {
                    arr.add(w);
                    wordCount++;
                }
            }
            KEYS.add(full);
        }

        for (String line : readLines(abbrIn)) {
            String[] kv = splitTab(line);
            if (kv.length < 2) continue;
            String key = kv[0].trim().toLowerCase();
            if (key.isEmpty()) continue;
            ArrayList<String> arr = ABBR.computeIfAbsent(key, k -> new ArrayList<>());
            for (String w : splitPipe(kv[1])) if (!arr.contains(w)) arr.add(w);
        }

        for (String line : readLines(assocIn)) {
            String[] kv = splitTab(line);
            if (kv.length < 2) continue;
            String key = kv[0].trim();
            if (key.isEmpty()) continue;
            ArrayList<String> arr = ASSOC.computeIfAbsent(key, k -> new ArrayList<>());
            for (String w : splitPipe(kv[1])) if (!arr.contains(w) && arr.size() < 10) arr.add(w);
        }

        for (String line : readLines(syllIn)) {
            String s = line.trim().toLowerCase();
            if (!s.isEmpty()) SYLL.add(s);
        }

        rebuildIndex();
        loadMs = System.currentTimeMillis() - t0;
        loaded = true;
    }

    private void rebuildIndex() {
        BY_FIRST_LEN.clear();
        for (String k : WORDS.keySet()) {
            if (k.isEmpty()) continue;
            char f = Fuzzy.normChar(k.charAt(0));
            BY_FIRST_LEN.computeIfAbsent(f, x -> new HashMap<>())
                    .computeIfAbsent(k.length(), x -> new ArrayList<>()).add(k);
        }
    }

    private String abbrOf(String full) {
        if (full.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < full.length()) {
            int best = 0;
            for (int len = Math.min(6, full.length() - i); len >= 1; len--) {
                if (SYLL.contains(full.substring(i, i + len))) {
                    best = len;
                    break;
                }
            }
            if (best == 0) best = 1;
            sb.append(full.charAt(i));
            i += best;
        }
        return sb.toString();
    }

    public static String pinyinOf(String word, Map<Character, String> rev) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            String s = rev.get(word.charAt(i));
            if (s == null) return null;
            sb.append(s);
        }
        String full = sb.toString();
        return full.isEmpty() ? null : full;
    }

    public void addImported(String word, String full) {
        if (!isHanWord(word) || full == null) return;
        String f = cleanSyllableText(full);
        if (f.isEmpty()) return;
        ArrayList<String> arr = WORDS.computeIfAbsent(f, k -> new ArrayList<>());
        if (!arr.contains(word)) {
            arr.add(0, word);
            wordCount++;
            IMPORTED.add(word);
        }
        KEYS.add(f);
        String ab = abbrOf(f);
        if (!ab.isEmpty()) {
            ArrayList<String> a2 = ABBR.computeIfAbsent(ab, k -> new ArrayList<>());
            if (!a2.contains(word)) a2.add(0, word);
        }
        char fc = Fuzzy.normChar(f.charAt(0));
        ArrayList<String> lst = BY_FIRST_LEN.computeIfAbsent(fc, x -> new HashMap<>())
                .computeIfAbsent(f.length(), x -> new ArrayList<>());
        if (!lst.contains(f)) lst.add(0, f);
    }

    public void addUserWord(String word) {
        if (!isHanWord(word)) return;
        String full = pinyinOf(word, SINGLE_REV);
        if (full == null && word.length() == 1) {
            String s = SINGLE_REV.get(word.charAt(0));
            if (s != null) full = s;
        }
        if (full != null) {
            USER_WORDS.add(word);
            addImported(word, full);
        }
    }

    public void removeUserWord(String word) {
        USER_WORDS.remove(word);
        IMPORTED.remove(word);
        for (ArrayList<String> arr : WORDS.values()) arr.remove(word);
        for (ArrayList<String> arr : ABBR.values()) arr.remove(word);
    }

    public int importText(String text) {
        if (text == null) return 0;
        int n = 0;
        String[] lines = text.split("\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            String[] tab = splitTab(line);
            if (tab.length >= 2) {
                String a = tab[0].trim();
                String b = tab[1].trim();
                boolean aPy = looksPinyin(a);
                boolean bPy = looksPinyin(b);
                if (aPy && !bPy) {
                    for (String w : splitPipe(b)) {
                        if (isHanWord(w)) {
                            addImported(w, a);
                            n++;
                        }
                    }
                } else if (bPy && !aPy) {
                    if (isHanWord(a)) {
                        addImported(a, b);
                        n++;
                    }
                } else if (!aPy && !bPy) {
                    int c = importSpaceFormat(line);
                    if (c > 0) n += c;
                    else if (isHanWord(a)) {
                        String full = pinyinOf(a, SINGLE_REV);
                        if (full != null) {
                            addImported(a, full);
                            n++;
                        }
                    }
                }
                continue;
            }
            int c = importSpaceFormat(line);
            if (c > 0) {
                n += c;
            } else if (isHanWord(line)) {
                String full = pinyinOf(line, SINGLE_REV);
                if (full != null) {
                    addImported(line, full);
                    n++;
                }
            }
        }
        rebuildIndex();
        return n;
    }

    private int importSpaceFormat(String line) {
        String[] sp = line.split("\\s+");
        if (sp.length < 2) return 0;
        if (!isHanWord(sp[0])) return 0;
        StringBuilder py = new StringBuilder();
        for (int i = 1; i < sp.length; i++) {
            String t = sp[i];
            if (isPinyinToken(t)) py.append(t.toLowerCase());
            else break;
        }
        if (py.length() == 0) return 0;
        addImported(sp[0], py.toString());
        return 1;
    }

    private static boolean looksPinyin(String s) {
        if (s.isEmpty()) return false;
        boolean any = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\'') continue;
            if (c >= 'a' && c <= 'z') {
                any = true;
            } else if (c >= 'A' && c <= 'Z') {
                any = true;
            } else {
                return false;
            }
        }
        return any;
    }

    private static boolean isPinyinToken(String s) {
        if (s.isEmpty() || s.length() > 6) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) return false;
        }
        return true;
    }

    public String exportAll() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, ArrayList<String>> e : WORDS.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            sb.append(e.getKey()).append('\t');
            for (int i = 0; i < e.getValue().size(); i++) {
                if (i > 0) sb.append('|');
                sb.append(e.getValue().get(i));
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public String statsText() {
        return "词库 " + bucketCount() + " 桶 / " + wordCount() + " 词 · 导入 "
                + importedCount() + " · 自造 " + userWordCount() + " · 加载 " + loadMs + "ms";
    }

    public static Map<Character, String> buildReverse(InputStream singleIn) throws IOException {
        HashMap<Character, String> rev = new HashMap<>();
        for (String line : readLines(singleIn)) {
            String[] kv = splitTab(line);
            if (kv.length < 2) continue;
            String syl = kv[0].trim().toLowerCase();
            for (int i = 0; i < kv[1].length(); i++) {
                char c = kv[1].charAt(i);
                if (isHan(c) && !rev.containsKey(c)) rev.put(c, syl);
            }
        }
        return rev;
    }
}