package com.meiyan.ime.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

public final class PinyinEngine {
    public static final int MAX_CANDIDATES = 30;
    private final DictRepo repo;
    private final MemoryStore memory;
    private boolean fuzzyEnabled = true;

    private final HashMap<String, ArrayList<String>> formCache = new HashMap<>();
    private final HashMap<String, ArrayList<String>> splitCache = new HashMap<>();
    private static final ArrayList<String> NO_SPLIT = new ArrayList<>();

    public PinyinEngine(DictRepo repo, MemoryStore memory) {
        this.repo = repo;
        this.memory = memory;
    }

    public DictRepo repo() {
        return repo;
    }

    public void setFuzzyEnabled(boolean v) {
        fuzzyEnabled = v;
    }

    public boolean isFuzzyEnabled() {
        return fuzzyEnabled;
    }

    public List<String> candidates(String input) {
        ArrayList<String> empty = new ArrayList<>();
        if (input == null || input.isEmpty() || !repo.isLoaded()) return empty;
        String q = input.toLowerCase();
        ArrayList<String> list = new ArrayList<>();
        HashSet<String> seen = new HashSet<>();

        appendSrc(list, seen, repo.WORDS.get(q), 12);
        appendSrc(list, seen, repo.ABBR.get(q), 24);

        if (list.size() < 10 && q.length() >= 3) {
            int added = 0;
            outer:
            for (String k : repo.KEYS.tailSet(q, false)) {
                if (!k.startsWith(q)) break;
                if (k.length() - q.length() > 4) break;
                ArrayList<String> arr = repo.WORDS.get(k);
                if (arr != null) {
                    for (int i = 0; i < arr.size() && i < 2; i++) {
                        if (appendWord(list, seen, arr.get(i))) {
                            if (++added >= 8) break outer;
                        }
                    }
                }
            }
        }

        boolean roomy = list.size() < 10;
        int budget = roomy ? 16 : 10;
        int maxMiss = roomy ? 4 : 1;
        if (fuzzyEnabled) fuzzy(q, maxMiss, budget, list, seen);

        ArrayList<String> syls = splitSyllables(q);

        if (syls != null && syls.size() >= 2) {
            StringBuilder sb = new StringBuilder();
            boolean ok = true;
            for (String s : syls) {
                ArrayList<Character> arr = repo.SINGLE.get(s);
                if (arr == null || arr.isEmpty()) {
                    ok = false;
                    break;
                }
                sb.append(arr.get(0));
            }
            if (ok && sb.length() >= 2) appendWord(list, seen, sb.toString());
        }

        if (list.size() < MAX_CANDIDATES && syls != null && !syls.isEmpty()) {
            String first = syls.get(0);
            ArrayList<Character> arr = repo.SINGLE.get(first);
            if (arr != null) {
                for (Character c : arr) {
                    appendWord(list, seen, String.valueOf(c));
                    if (list.size() >= MAX_CANDIDATES) break;
                }
            }
        }

        if (list.size() > MAX_CANDIDATES) list = new ArrayList<>(list.subList(0, MAX_CANDIDATES));
        return memory.reorder(list);
    }

    private static boolean appendWord(ArrayList<String> list, HashSet<String> seen, String w) {
        if (w == null || w.isEmpty()) return false;
        if (seen.add(w)) {
            list.add(w);
            return true;
        }
        return false;
    }

    private static void appendSrc(ArrayList<String> list, HashSet<String> seen, List<String> src, int max) {
        if (src == null) return;
        int n = 0;
        for (String w : src) {
            appendWord(list, seen, w);
            if (++n >= max) break;
        }
    }

    private void fuzzy(String q, int maxMiss, int budget, ArrayList<String> list, HashSet<String> seen) {
        String norm = Fuzzy.normChars(q);
        if (norm.isEmpty()) return;
        HashMap<Integer, ArrayList<String>> byLen = repo.BY_FIRST_LEN.get(norm.charAt(0));
        if (byLen == null) return;
        int order = 0;
        ArrayList<Hit> hits = new ArrayList<>();
        HashSet<String> seenKeys = new HashSet<>();
        for (String v : Fuzzy.variants(q)) {
            if (!repo.WORDS.containsKey(v) || !seenKeys.add(v)) continue;
            ArrayList<String> syls = splitSyllables(v);
            if (syls == null || syls.isEmpty()) continue;
            int miss = missScore(norm, syls);
            if (miss <= maxMiss) hits.add(new Hit(v, miss, order++));
        }
        for (int len = norm.length(); len <= norm.length() + maxMiss; len++) {
            ArrayList<String> keys = byLen.get(len);
            if (keys == null) continue;
            int scanned = 0;
            for (String key : keys) {
                if (++scanned > 600) break;
                if (key.length() != len || !seenKeys.add(key)) continue;
                ArrayList<String> syls = splitSyllables(key);
                if (syls == null || syls.isEmpty()) continue;
                int miss = missScore(norm, syls);
                if (miss <= maxMiss) hits.add(new Hit(key, miss, order++));
            }
        }
        hits.sort((a, b) -> a.miss != b.miss
                ? Integer.compare(a.miss, b.miss)
                : Integer.compare(a.order, b.order));
        ArrayList<String> pre = new ArrayList<>();
        ArrayList<String> rest = new ArrayList<>();
        int zeroLimit = Math.max(1, budget - 4);
        int zeroUsed = 0;
        int restUsed = 0;
        for (Hit hit : hits) {
            ArrayList<String> arr = repo.WORDS.get(hit.key);
            if (arr == null) continue;
            if (hit.miss == 0) {
                for (String w : arr) {
                    if (zeroUsed >= zeroLimit) break;
                    if (seen.contains(w)) continue;
                    pre.add(w);
                    zeroUsed++;
                }
            } else {
                if (zeroUsed + restUsed >= budget) break;
                for (String w : arr) {
                    if (zeroUsed + restUsed >= budget) break;
                    if (seen.contains(w)) continue;
                    rest.add(w);
                    restUsed++;
                }
            }
        }
        ArrayList<String> preAccepted = new ArrayList<>();
        for (String w : pre) if (seen.add(w)) preAccepted.add(w);
        ArrayList<String> restAccepted = new ArrayList<>();
        for (String w : rest) if (seen.add(w)) restAccepted.add(w);
        list.addAll(preAccepted);
        list.addAll(restAccepted);
    }

    private static final class Hit {
        final String key;
        final int miss;
        final int order;

        Hit(String key, int miss, int order) {
            this.key = key;
            this.miss = miss;
            this.order = order;
        }
    }

    private int missScore(String input, ArrayList<String> syls) {
        int n = input.length();
        int inf = 1000;
        int[] dp = new int[n + 1];
        Arrays.fill(dp, inf);
        dp[0] = 0;
        int[] best = new int[64];
        for (String syl : syls) {
            int[] nd = new int[n + 1];
            Arrays.fill(nd, inf);
            ArrayList<String> forms = formsOf(syl);
            for (int p = 0; p <= n; p++) {
                int baseCost = dp[p];
                if (baseCost >= inf) continue;
                for (String f : forms) {
                    int m = Math.min(f.length(), 7);
                    int maxI = Math.min(m, n - p);
                    Arrays.fill(best, inf);
                    best[0] = 0;
                    for (int j = 1; j <= m; j++) best[j] = j;
                    for (int i = 1; i <= maxI; i++) {
                        int row = i << 3;
                        int prev = row - 8;
                        char ic = input.charAt(p + i - 1);
                        for (int j = 1; j <= m; j++) {
                            int v = best[row + j - 1] + 1;
                            if (f.charAt(j - 1) == ic && best[prev + j - 1] < v) {
                                v = best[prev + j - 1];
                            }
                            best[row + j] = v;
                        }
                    }
                    for (int i = 0; i <= maxI; i++) {
                        int score = baseCost + best[(i << 3) + m];
                        if (score < nd[p + i]) nd[p + i] = score;
                    }
                }
            }
            dp = nd;
        }
        return dp[n];
    }

    private ArrayList<String> formsOf(String syl) {
        ArrayList<String> c = formCache.get(syl);
        if (c != null) return c;
        ArrayList<String> v = Fuzzy.variants(syl);
        LinkedHashSet<String> set = new LinkedHashSet<>(v);
        for (String s : v) set.add(Fuzzy.normChars(s));
        c = new ArrayList<>(set);
        if (formCache.size() > 4096) formCache.clear();
        formCache.put(syl, c);
        return c;
    }

    public ArrayList<String> splitSyllables(String s) {
        if (s == null || s.isEmpty()) return new ArrayList<>();
        ArrayList<String> cached = splitCache.get(s);
        if (cached != null) return cached;
        ArrayList<String> out = new ArrayList<>();
        int i = 0;
        boolean ok = true;
        while (i < s.length()) {
            int best = 0;
            for (int len = Math.min(6, s.length() - i); len >= 1; len--) {
                if (repo.SYLL.contains(s.substring(i, i + len))) {
                    best = len;
                    break;
                }
            }
            if (best == 0) {
                ok = false;
                break;
            }
            out.add(s.substring(i, i + best));
            i += best;
        }
        ArrayList<String> result = ok ? out : NO_SPLIT;
        if (splitCache.size() > 1024) splitCache.clear();
        splitCache.put(s, result);
        return result;
    }

    public List<String> associate(String lastChar) {
        ArrayList<String> arr = repo.ASSOC.get(lastChar);
        if (arr == null || arr.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(arr.subList(0, Math.min(10, arr.size())));
    }

    public static String assocSuffix(String word, String candidate) {
        if (candidate == null || candidate.isEmpty()) return "";
        if (word == null || word.isEmpty()) return candidate;
        if (candidate.equals(word)) return "";
        if (candidate.startsWith(word)) return candidate.substring(word.length());
        char last = word.charAt(word.length() - 1);
        if (candidate.length() == 1) {
            return candidate.charAt(0) == last ? "" : candidate;
        }
        if (candidate.charAt(0) == last) return candidate.substring(1);
        return candidate;
    }

    public boolean isFirstCharCandidate(String input, String w) {
        if (w == null || w.length() != 1) return false;
        ArrayList<String> syls = splitSyllables(input);
        if (syls == null || syls.size() < 2) return false;
        ArrayList<Character> arr = repo.SINGLE.get(syls.get(0));
        return arr != null && arr.contains(w.charAt(0));
    }

    public List<String> secondChars(String prev, String nextSyllable) {
        ArrayList<String> out = new ArrayList<>();
        if (prev == null || prev.isEmpty() || nextSyllable == null) return out;
        ArrayList<Character> base = repo.SINGLE.get(nextSyllable);
        if (base == null || base.isEmpty()) return out;
        LinkedHashSet<Character> order = repo.NEXT.get(prev.charAt(0));
        if (order != null) {
            for (Character c : order) {
                if (base.contains(c)) out.add(String.valueOf(c));
                if (out.size() >= MAX_CANDIDATES) return out;
            }
        }
        for (Character c : base) {
            String s = String.valueOf(c);
            if (!out.contains(s)) out.add(s);
            if (out.size() >= MAX_CANDIDATES) break;
        }
        return out;
    }

    public boolean hasPinyinHit(String q) {
        if (q == null || q.isEmpty()) return false;
        String s = q.toLowerCase();
        if (repo.WORDS.containsKey(s) || repo.ABBR.containsKey(s)) return true;
        for (String k : repo.KEYS.tailSet(s, false)) {
            if (!k.startsWith(s)) break;
            if (k.length() - s.length() <= 4) return true;
            break;
        }
        return false;
    }
}