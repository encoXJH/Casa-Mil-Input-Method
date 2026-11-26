import com.meiyan.ime.engine.DictRepo;
import com.meiyan.ime.engine.MemoryStore;
import com.meiyan.ime.engine.PinyinEngine;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

public class EngineTest {
    static DictRepo repo = new DictRepo();
    static MemoryStore mem = new MemoryStore();
    static PinyinEngine eng;
    static int pass = 0;
    static int fail = 0;

    static InputStream f(String p) throws Exception {
        return new FileInputStream(new File(p));
    }

    static void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
            System.out.println("[PASS] " + name + "  " + (detail == null ? "" : detail));
        } else {
            fail++;
            System.out.println("[FAIL] " + name + "  " + (detail == null ? "" : detail));
        }
    }

    static void first(String q, String expect) {
        List<String> c = eng.candidates(q);
        boolean ok = !c.isEmpty() && c.get(0).equals(expect);
        check("首位 " + q + " -> " + expect, ok, head(c));
    }

    static void contains(String q, String expect) {
        List<String> c = eng.candidates(q);
        boolean ok = c.contains(expect);
        check("包含 " + q + " -> " + expect, ok, head(c));
    }

    static String head(List<String> c) {
        StringBuilder sb = new StringBuilder("cand=");
        for (int i = 0; i < Math.min(8, c.size()); i++) {
            if (i > 0) sb.append(',');
            sb.append(c.get(i));
        }
        sb.append(" size=").append(c.size());
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        String d = "app/src/main/assets/pinyin/";
        long t0 = System.currentTimeMillis();
        repo.load(f(d + "word.txt"), f(d + "abbr.txt"), f(d + "single.txt"),
                f(d + "assoc.txt"), f(d + "poly.txt"), f(d + "syll.txt"));
        long load = System.currentTimeMillis() - t0;
        eng = new PinyinEngine(repo, mem);
        System.out.println("LOAD " + load + "ms  " + repo.statsText());

        first("lt", "聊天");
        first("nh", "你好");
        contains("liaot", "聊天");
        first("pyin", "拼音");
        first("nianxi", "联系");
        contains("cang", "长");
        contains("zhang", "长");
        contains("zhang", "张");
        contains("zang", "藏");
        contains("zang", "张");
        contains("fuiyi", "会议");
        contains("zho", "中");

        List<String> as = eng.associate("天");
        check("联想 天 含 聊天记录", as.contains("聊天记录"), as.toString());

        List<String> lx0 = eng.candidates("lx");
        check("lx 初始首位 联系", !lx0.isEmpty() && lx0.get(0).equals("联系"), head(lx0));
        int idx = lx0.indexOf("联想");
        check("lx 初始 联想 约第11位", idx >= 8 && idx <= 14, "index=" + idx);

        mem.learn("联想");
        mem.learn("联想");
        mem.learn("联想");
        List<String> lx1 = eng.candidates("lx");
        check("lx 记忆3次后首位 联想", !lx1.isEmpty() && lx1.get(0).equals("联想"), head(lx1));

        String[] qs = {"a", "ni", "hao", "zhongguo", "yi", "shi", "liaotianjilu", "xian"};
        for (String q : qs) {
            List<String> c = eng.candidates(q);
            check("候选<=30 " + q, c.size() <= 30, "size=" + c.size());
        }

        DictRepo r2 = new DictRepo();
        r2.SINGLE_REV.putAll(repo.SINGLE_REV);
        for (String s : repo.SYLL) r2.SYLL.add(s);
        String sample = "测试词\tce shi ci\nceshi\t测试|测试员\n第三个词 ce shi ci 1000\n你好\n";
        int n = r2.importText(sample);
        check("导入4格式合计>=4条", n >= 4, "n=" + n);
        check("导入立即生效 测试词", r2.WORDS.containsKey("ceshici"),
                r2.WORDS.containsKey("ceshici") ? r2.WORDS.get("ceshici").toString() : "missing");
        check("导入单候选行收下 测试员", r2.WORDS.containsKey("ceshi") && r2.WORDS.get("ceshi").contains("测试员"),
                String.valueOf(r2.WORDS.get("ceshi")));

        int it = 200;
        long t = System.nanoTime();
        for (int i = 0; i < it; i++) {
            eng.candidates("nihao");
            eng.candidates("pyin");
            eng.candidates("zhongguo");
        }
        long ns = System.nanoTime() - t;
        double us = ns / 1000.0 / (it * 3);
        check("热态候选平均 <5ms", us < 5000, String.format("%.1f us/call", us));

        System.out.println("RESULT pass=" + pass + " fail=" + fail);
    }
}