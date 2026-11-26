package com.meiyan.ime.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MemoryStore {
    private final LinkedHashMap<String, Integer> zh = new LinkedHashMap<>();
    private final LinkedHashMap<String, Integer> en = new LinkedHashMap<>();
    private int pendingWrites;
    public static final int MAX_WORDS = 600;
    public static final int FLUSH_EVERY = 6;

    public synchronized int countOf(String w) {
        Integer v = zh.get(w);
        return v == null ? 0 : v;
    }

    public synchronized int englishCountOf(String w) {
        Integer v = en.get(w.toLowerCase());
        return v == null ? 0 : v;
    }

    public synchronized boolean learn(String text) {
        if (text == null) return false;
        if (text.length() > 8) return false;
        if (!DictRepo.hasHan(text)) return false;
        if (DictRepo.isPunctOnly(text)) return false;
        boolean onlyHan = DictRepo.isHanWord(text);
        if (!onlyHan) {
            int han = 0, other = 0;
            for (int i = 0; i < text.length(); i++) {
                if (DictRepo.isHan(text.charAt(i))) han++;
                else if (!DictRepo.isAsciiLetter(text.charAt(i)) && !DictRepo.isAsciiDigit(text.charAt(i))) other++;
            }
            if (han == 0 || other > 0) return false;
        }
        zh.merge(text, 1, Integer::sum);
        pendingWrites++;
        trim();
        return true;
    }

    public synchronized void learnEnglish(String w) {
        if (w == null || w.length() < 2 || w.length() > 16) return;
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            if (!DictRepo.isAsciiLetter(c)) return;
        }
        en.merge(w.toLowerCase(), 1, Integer::sum);
        pendingWrites++;
        if (en.size() > 200) trimEnglish();
    }

    private void trim() {
        if (zh.size() <= MAX_WORDS) return;
        ArrayList<Map.Entry<String, Integer>> list = new ArrayList<>(zh.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        LinkedHashMap<String, Integer> keep = new LinkedHashMap<>();
        for (int i = 0; i < MAX_WORDS && i < list.size(); i++) keep.put(list.get(i).getKey(), list.get(i).getValue());
        zh.clear();
        zh.putAll(keep);
    }

    private void trimEnglish() {
        ArrayList<Map.Entry<String, Integer>> list = new ArrayList<>(en.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        LinkedHashMap<String, Integer> keep = new LinkedHashMap<>();
        for (int i = 0; i < 200 && i < list.size(); i++) keep.put(list.get(i).getKey(), list.get(i).getValue());
        en.clear();
        en.putAll(keep);
    }

    public synchronized boolean needFlush() {
        return pendingWrites >= FLUSH_EVERY;
    }

    public synchronized void flushDone() {
        pendingWrites = 0;
    }

    public synchronized void forceFlushMark() {
        pendingWrites = 0;
    }

    public synchronized String serialize() {
        StringBuilder sb = new StringBuilder();
        ArrayList<Map.Entry<String, Integer>> list = new ArrayList<>(zh.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        for (Map.Entry<String, Integer> e : list) {
            sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    public synchronized String serializeEnglish() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : en.entrySet()) {
            sb.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        return sb.toString();
    }

    public synchronized void load(String data) {
        zh.clear();
        if (data == null) return;
        for (String line : data.split("\n")) {
            if (line.isEmpty()) continue;
            int i = line.indexOf('\t');
            if (i <= 0) continue;
            try {
                zh.put(line.substring(0, i), Integer.parseInt(line.substring(i + 1).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public synchronized void loadEnglish(String data) {
        en.clear();
        if (data == null) return;
        for (String line : data.split("\n")) {
            if (line.isEmpty()) continue;
            int i = line.indexOf('\t');
            if (i <= 0) continue;
            try {
                en.put(line.substring(0, i), Integer.parseInt(line.substring(i + 1).trim()));
            } catch (NumberFormatException ignored) {
            }
        }
    }

    public synchronized void clear() {
        zh.clear();
        en.clear();
        pendingWrites = 0;
    }

    public synchronized int size() {
        return zh.size();
    }

    public synchronized int memorySize() {
        return zh.size();
    }

    public synchronized void clearMemory() {
        clear();
    }

    public synchronized List<String> topWords(int n) {
        ArrayList<Map.Entry<String, Integer>> list = new ArrayList<>(zh.entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < n && i < list.size(); i++) out.add(list.get(i).getKey());
        return out;
    }

    public synchronized List<String> englishTop(int n) {
        ArrayList<Map.Entry<String, Integer>> list = new ArrayList<>(en.entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<String, Integer> e) -> e.getValue()).reversed());
        ArrayList<String> out = new ArrayList<>();
        for (int i = 0; i < n && i < list.size(); i++) out.add(list.get(i).getKey());
        return out;
    }

    public synchronized List<String> reorder(List<String> candidates) {
        if (zh.isEmpty() || candidates.size() < 2) return candidates;
        ArrayList<String> out = new ArrayList<>(candidates);
        out.sort((a, b) -> Integer.compare(countOf(b), countOf(a)));
        return out;
    }

    public synchronized void topWordsInto(List<String> out, int n) {
        ArrayList<String> t = new ArrayList<>(topWords(n));
        Collections.reverse(t);
        out.addAll(t);
    }
}