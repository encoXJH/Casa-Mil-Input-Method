package com.meiyan.ime;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.meiyan.ime.ai.DeepSeekClient;
import com.meiyan.ime.engine.DictRepo;
import com.meiyan.ime.engine.MemoryStore;
import com.meiyan.ime.engine.TouchModel;
import com.meiyan.ime.store.Prefs;
import com.meiyan.ime.theme.MiraTheme;
import com.meiyan.ime.voice.VoiceEngine;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class SettingsActivity extends Activity {
    private static final int REQ_IMPORT = 11;
    private static final int REQ_EXPORT = 12;
    private static final int REQ_EXTRACT = 13;
    private static final int REQ_AUDIO = 21;

    private Prefs prefs;
    private volatile DictRepo repo = new DictRepo();
    private final MemoryStore memory = new MemoryStore();
    private final TouchModel touch = new TouchModel();
    private LinearLayout root;
    private LinearLayout sectionContent;
    private LinearLayout expandedContent;
    private TextView dictStat;
    private TextView memStat;
    private TextView touchStat;
    private TextView voiceStat;
    private volatile boolean repoReady;
    private volatile HashSet<String> wordIndex = new HashSet<>();
    private String pendingExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new Prefs(this);
        MiraTheme.applyTheme(prefs.getInt(Prefs.THEME, MiraTheme.MIRA));
        MiraTheme.freqColor = prefs.getBool(Prefs.FREQ_COLOR, true);
        memory.load(prefs.getString(Prefs.MEMORY, ""));
        memory.loadEnglish(prefs.getString(Prefs.MEMORY_EN, ""));
        touch.importJson(prefs.getString(Prefs.TOUCH_JSON, ""));
        touch.setEnabled(prefs.getBool(Prefs.TOUCH_ON, true));
        buildUi();
        reloadRepoFromFiles();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(MiraTheme.PAGE_BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(26), dp(18), dp(40));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
        try {
            getWindow().setStatusBarColor(MiraTheme.PAGE_BG);
        } catch (Throwable ignored) {
        }

        TextView head = new TextView(this);
        head.setText("米拉之家");
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        head.setTag("title");
        root.addView(head);
        TextView sub = new TextView(this);
        sub.setText("v1.0 · 纯本地中文输入法");
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setTag("sub");
        root.addView(sub);

        addGuide();
        addThemeSection();
        addAiSection();
        addAiButtonsSection();
        addDictSection();
        addMemorySection();
        addTouchSection();
        addVoiceSection();
        addTestSection();
        addDiagSection();
        applyPageColors();
    }

    private void addGuide() {
        section("启用与切换");
        body("① 去系统设置启用本输入法；② 在任意输入框调出输入法选择器。");
        rowButtons(new String[]{"① 去系统设置启用", "② 选择/切换输入法"},
                new View.OnClickListener[]{v -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
                    } catch (Throwable t) {
                        toast("无法打开系统设置");
                    }
                }, v -> {
                    try {
                        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showInputMethodPicker();
                    } catch (Throwable t) {
                        toast("无法打开选择器");
                    }
                }});
    }

    private void addThemeSection() {
        section("外观主题");
        body("三套完整色板，键盘与设置页整页联动。");
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"米拉之家", "敦煌", "莫兰迪"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView b = button(names[i], v -> {
                prefs.putInt(Prefs.THEME, idx);
                MiraTheme.applyTheme(idx);
                applyPageColors();
                toast("已切换：" + MiraTheme.name(idx));
            });
            b.setLayoutParams(new LinearLayout.LayoutParams(0, dp(40), 1f));
            row.addView(b);
        }
        addViewToSection(row);
        switchRow("频次上色", prefs.getBool(Prefs.FREQ_COLOR, true), (btn, checked) -> {
            prefs.putBool(Prefs.FREQ_COLOR, checked);
            MiraTheme.freqColor = checked;
            toast(checked ? "频次上色已开" : "频次上色已关");
        });
    }

    private void addAiSection() {
        section("AI 文段改写（DeepSeek）");
        body("仅在主动点击改写按钮时联网；Key 只存本机，不以明文展示。\n按钮名称 / 提示词 / 资料在下一节自定义。");
        switchRow("启用 AI 改写", prefs.getBool(Prefs.AI_ON, false), (b, c) -> {
            prefs.putBool(Prefs.AI_ON, c);
            toast(c ? "AI 改写已开" : "AI 改写已关");
        });
        editRow("API Key", prefs.getString(Prefs.AI_KEY, ""), true, v -> prefs.putString(Prefs.AI_KEY, v));
        editRow("接口 URL", prefs.getString(Prefs.AI_URL, Prefs.DEFAULT_URL), false, v -> prefs.putString(Prefs.AI_URL, v));
        editRow("模型", prefs.getString(Prefs.AI_MODEL, Prefs.DEFAULT_MODEL), false, v -> prefs.putString(Prefs.AI_MODEL, v));
        rowButtons(new String[]{"测试连接"}, new View.OnClickListener[]{v -> testConnection()});
    }

    private void addAiButtonsSection() {
        section("AI 按钮自定义");
        List<String> names = Prefs.splitPipe(prefs.getString(Prefs.AI_NAMES, Prefs.DEFAULT_NAMES), 4);
        List<String> prompts = Prefs.splitPipe(prefs.getString(Prefs.AI_PROMPTS, Prefs.DEFAULT_PROMPTS), 4);
        List<String> docs = Prefs.splitPipe(prefs.getString(Prefs.AI_DOCS, Prefs.DEFAULT_DOCS), 4);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            final String tag = "按钮 " + (i + 1);
            editRow(tag + " 名称", names.get(i), false,
                    v -> saveAiList(Prefs.AI_NAMES, Prefs.DEFAULT_NAMES, idx, v));
            editRow(tag + " 提示词", prompts.get(i), false,
                    v -> saveAiList(Prefs.AI_PROMPTS, Prefs.DEFAULT_PROMPTS, idx, v));
            editRow(tag + " 资料", docs.get(i), false,
                    v -> saveAiList(Prefs.AI_DOCS, Prefs.DEFAULT_DOCS, idx, v));
        }
        rowButtons(new String[]{"重置按钮"}, new View.OnClickListener[]{v -> {
            prefs.putString(Prefs.AI_NAMES, Prefs.DEFAULT_NAMES);
            prefs.putString(Prefs.AI_PROMPTS, Prefs.DEFAULT_PROMPTS);
            prefs.putString(Prefs.AI_DOCS, Prefs.DEFAULT_DOCS);
            toast("已重置按钮");
            recreate();
        }});
    }

    private void saveAiList(String key, String def, int idx, String value) {
        List<String> list = Prefs.splitPipe(prefs.getString(key, def), 4);
        String safe = value == null ? "" : value.replace('|', '｜');
        list.set(idx, safe);
        prefs.putString(key, Prefs.join(list));
    }

    private void addDictSection() {
        section("词库");
        dictStat = body("统计加载中…");
        rowButtons(new String[]{"导入词库", "导出备份", "从文档提取新词"},
                new View.OnClickListener[]{v -> pickFile(REQ_IMPORT), v -> exportDict(), v -> pickFile(REQ_EXTRACT)});
        rowButtons(new String[]{"清空导入词库"}, new View.OnClickListener[]{v -> {
            new AlertDialog.Builder(this).setTitle("清空导入词库")
                    .setMessage("只清空导入词，不删除自造词，确认继续？")
                    .setPositiveButton("确认", (d, w) -> {
                        clearImportedFiles();
                        reloadRepoFromFiles();
                        MiraImeService.reloadFromSettings();
                        toast("导入词库已清空");
                    })
                    .setNegativeButton("取消", null).show();
        }});
        body("格式：① 词<TAB>拼音 ② 拼音<TAB>词|词 ③ 词 拼音 词频 ④ 只有词（自动注音）。# 为注释，可混排。");
        final EditText input = new EditText(this);
        input.setHint("输入要添加的自造词");
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        input.setTextColor(MiraTheme.PAGE_INK);
        input.setHintTextColor(MiraTheme.PAGE_SUB);
        addViewToSection(input);
        rowButtons(new String[]{"添加自造词", "删除自造词"},
                new View.OnClickListener[]{v -> {
                    String w = input.getText().toString().trim();
                    if (!DictRepo.isHanWord(w)) {
                        toast("请输入纯中文词语");
                        return;
                    }
                    List<String> list = userWords();
                    if (!list.contains(w)) list.add(0, w);
                    prefs.putString(Prefs.USER_WORDS, Prefs.join(list));
                    if (repoReady) {
                        repo.addUserWord(w);
                        wordIndex.add(w);
                    }
                    input.setText("");
                    refreshStats();
                    MiraImeService.reloadFromSettings();
                    toast("已添加自造词");
                }, v -> {
                    String w = input.getText().toString().trim();
                    List<String> list = userWords();
                    boolean removed = list.remove(w);
                    prefs.putString(Prefs.USER_WORDS, Prefs.join(list));
                    input.setText("");
                    if (removed) {
                        reloadRepoFromFiles();
                        MiraImeService.reloadFromSettings();
                        toast("已删除自造词");
                    } else {
                        toast("没有这个词");
                    }
                }});
    }

    private void addMemorySection() {
        section("联想与纠错");
        memStat = body("");
        switchRow("模糊音", prefs.getBool(Prefs.FUZZY, true), (b, c) -> {
            prefs.putBool(Prefs.FUZZY, c);
            toast(c ? "模糊音已开" : "模糊音已关");
        });
        rowButtons(new String[]{"清空记忆"}, new View.OnClickListener[]{v -> {
            memory.clear();
            prefs.putString(Prefs.MEMORY, "");
            prefs.putString(Prefs.MEMORY_EN, "");
            refreshStats();
            toast("记忆已清空");
        }});
    }

    private void addTouchSection() {
        section("按键纠错学习");
        touchStat = body("");
        switchRow("启用按键画像", prefs.getBool(Prefs.TOUCH_ON, true), (b, c) -> {
            prefs.putBool(Prefs.TOUCH_ON, c);
            touch.setEnabled(c);
            toast(c ? "画像已开" : "画像已关");
        });
        rowButtons(new String[]{"导出画像", "导入画像", "清空画像"},
                new View.OnClickListener[]{v -> exportTouch(), v -> importTouch(), v -> {
                    touch.clear();
                    prefs.putString(Prefs.TOUCH_JSON, "");
                    refreshStats();
                    toast("画像已清空");
                }});
    }

    private void addVoiceSection() {
        section("本地语音识别");
        voiceStat = body(voiceStatusText());
        rowButtons(new String[]{"授予麦克风权限"},
                new View.OnClickListener[]{v -> requestAudioPermission()});
    }

    private String voiceStatusText() {
        String perm = "未授权（可在系统设置中授予麦克风权限）";
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                perm = "已授权";
            }
        } catch (Throwable ignored) {
        }
        return "麦克风权限：" + perm
                + "\n模型：sherpa-onnx Paraformer（assets/model.int8.onnx）"
                + "\n当前状态：" + (VoiceEngine.modelInAssets(this)
                ? "模型已内置，离线识别、不上传" : "模型未内置，语音功能不可用");
    }

    private void requestAudioPermission() {
        try {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                toast("麦克风已授权");
                return;
            }
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        } catch (Throwable t) {
            toast("请在系统设置中授予麦克风权限");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO) {
            if (voiceStat != null) voiceStat.setText(voiceStatusText());
            boolean ok = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            toast(ok ? "麦克风已授权" : "未授权，可在系统设置中开启");
        }
    }

    private void addTestSection() {
        section("测试输入框");
        EditText test = new EditText(this);
        test.setHint("在这里测试输入法");
        test.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        test.setTextColor(MiraTheme.PAGE_INK);
        test.setHintTextColor(MiraTheme.PAGE_SUB);
        test.setBackground(round(MiraTheme.PAGE_SURFACE, 8));
        test.setPadding(dp(12), dp(10), dp(12), dp(10));
        test.setMinLines(2);
        test.setGravity(Gravity.TOP | Gravity.START);
        addViewToSection(test, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body("使用说明：字母键落下即上屏；空格轻点上屏首选/空格，长按语音，上划 AI 改写；逗号长按出三气泡；退格在无拼音时支持全选删除。");
    }

    private void addDiagSection() {
        section("诊断信息");
        rowButtons(new String[]{"复制诊断信息"}, new View.OnClickListener[]{v -> copyDiag()});
    }

    private void copyDiag() {
        String s = diagText();
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("meiyan-diag", s));
            toast("诊断信息已复制");
        } catch (Throwable t) {
            toast("复制失败");
        }
    }

    private String diagText() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        StringBuilder sb = new StringBuilder();
        sb.append("米拉之家 v1.0\n");
        sb.append("词库：").append(repoReady ? repo.statsText() : "加载中").append('\n');
        sb.append("记忆词数：").append(memory.size()).append('\n');
        sb.append("画像：").append(touch.statsText()).append('\n');
        sb.append("屏幕：").append(dm.widthPixels).append('x').append(dm.heightPixels)
                .append(" @").append(dm.densityDpi).append("dpi\n");
        sb.append("机型：").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("系统：Android ").append(Build.VERSION.RELEASE).append('\n');
        sb.append("语音：").append(VoiceEngine.modelInAssets(this) ? "模型已内置" : "模型未内置").append('\n');
        return sb.toString();
    }

    private void loadRepoAsync() {
        reloadRepoFromFiles();
    }

    private void reloadRepoFromFiles() {
        repoReady = false;
        if (dictStat != null) dictStat.setText("统计加载中…");
        new Thread(() -> {
            try {
                DictRepo r = buildRepo();
                HashSet<String> idx = buildIndex(r);
                repo = r;
                wordIndex = idx;
                repoReady = true;
            } catch (Throwable ignored) {
            }
            runOnUiThread(this::refreshStats);
        }, "settings-dict").start();
    }

    private DictRepo buildRepo() throws Exception {
        DictRepo r = new DictRepo();
        r.load(getAssets().open("pinyin/word.txt"),
                getAssets().open("pinyin/abbr.txt"),
                getAssets().open("pinyin/single.txt"),
                getAssets().open("pinyin/assoc.txt"),
                getAssets().open("pinyin/poly.txt"),
                getAssets().open("pinyin/syll.txt"));
        File dir = new File(getFilesDir(), "dicts");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) r.importText(readFile(f));
        }
        for (String w : userWords()) r.addUserWord(w);
        return r;
    }

    private HashSet<String> buildIndex(DictRepo r) {
        HashSet<String> set = new HashSet<>();
        for (List<String> arr : r.WORDS.values()) set.addAll(arr);
        for (List<String> arr : r.ABBR.values()) set.addAll(arr);
        return set;
    }

    private void rebuildWordIndex() {
        wordIndex = buildIndex(repo);
    }

    private void refreshStats() {
        if (dictStat != null) {
            dictStat.setText(repoReady ? repo.statsText() : "统计加载中…");
        }
        if (memStat != null) {
            memStat.setText("已学词 " + memory.size() + " · 最常用：" +
                    (memory.topWords(8).isEmpty() ? "暂无" : listText(memory.topWords(8))));
        }
        if (touchStat != null) {
            touchStat.setText(touch.statsText());
        }
        if (voiceStat != null && repoReady) {
            voiceStat.setText(voiceStatusText());
        }
    }

    private static String listText(List<String> l) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) sb.append('、');
            sb.append(l.get(i));
        }
        return sb.toString();
    }

    private void testConnection() {
        toast("正在测试连接…");
        new Thread(() -> {
            DeepSeekClient.Result r = DeepSeekClient.rewriteText(this, "只回复：连接正常", "test", "", true);
            runOnUiThread(() -> toast(r.ok ? "连接正常" : "连接失败：" + r.error));
        }, "ai-test").start();
    }

    private void pickFile(int req) {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/*");
            startActivityForResult(i, req);
        } catch (Throwable t) {
            toast("无法打开文件选择器");
        }
    }

    private void exportDict() {
        try {
            if (!repoReady) {
                toast("词库还在加载，请稍后再试");
                return;
            }
            pendingExport = repo.exportAll();
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TITLE, "meiyan-dict-backup.txt");
            startActivityForResult(i, REQ_EXPORT);
        } catch (Throwable t) {
            toast("导出失败");
        }
    }

    private void exportTouch() {
        pendingExport = touch.exportJson();
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "meiyan-touch.json");
            startActivityForResult(i, REQ_EXPORT + 100);
        } catch (Throwable t) {
            toast("导出失败");
        }
    }

    private void importTouch() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("*/*");
            startActivityForResult(i, REQ_IMPORT + 100);
        } catch (Throwable t) {
            toast("无法打开选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (requestCode == REQ_IMPORT) {
                String text = readUri(uri);
                File dir = new File(getFilesDir(), "dicts");
                if (!dir.exists()) dir.mkdirs();
                String name = "import-" + System.currentTimeMillis() + ".txt";
                writeFile(new File(dir, name), text);
                int n = 0;
                boolean ready = repoReady;
                if (ready) {
                    n = repo.importText(text);
                    rebuildWordIndex();
                    refreshStats();
                }
                MiraImeService.reloadFromSettings();
                toast(ready ? "已导入 " + n + " 条（已立即生效）" : "已保存，词库加载后生效");
            } else if (requestCode == REQ_EXTRACT) {
                String text = readUri(uri);
                int n = extractWords(text);
                toast("提取 " + n + " 个新词到自造词");
            } else if (requestCode == REQ_EXPORT) {
                writeUri(uri, pendingExport == null ? "" : pendingExport);
                toast("词库备份已导出");
            } else if (requestCode == REQ_EXPORT + 100) {
                writeUri(uri, pendingExport == null ? "" : pendingExport);
                toast("画像已导出");
            } else if (requestCode == REQ_IMPORT + 100) {
                String text = readUri(uri);
                int n = touch.importJson(text);
                prefs.putString(Prefs.TOUCH_JSON, touch.exportJson());
                refreshStats();
                toast("画像已导入 " + n + " 个键");
            }
        } catch (Throwable t) {
            toast("操作未完成：" + t.getClass().getSimpleName());
        }
    }

    private int extractWords(String text) {
        List<String> user = userWords();
        ArrayList<String> addedWords = new ArrayList<>();
        int added = 0;
        StringBuilder han = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : '\n';
            if (DictRepo.isHan(c)) {
                han.append(c);
            } else {
                String w = han.toString();
                han.setLength(0);
                if (w.length() >= 2 && w.length() <= 6 && !user.contains(w) && !wordExists(w)) {
                    user.add(0, w);
                    addedWords.add(w);
                    added++;
                    if (added >= 200) break;
                }
            }
        }
        if (added > 0) {
            prefs.putString(Prefs.USER_WORDS, Prefs.join(user));
            if (repoReady) {
                for (String w : addedWords) {
                    repo.addUserWord(w);
                    wordIndex.add(w);
                }
            }
            refreshStats();
            MiraImeService.reloadFromSettings();
        }
        return added;
    }

    private boolean wordExists(String w) {
        return repoReady && wordIndex.contains(w);
    }

    private List<String> userWords() {
        ArrayList<String> out = new ArrayList<>();
        for (String s : Prefs.splitPipe(prefs.getString(Prefs.USER_WORDS, ""), 500)) {
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private void clearImportedFiles() {
        File dir = new File(getFilesDir(), "dicts");
        File[] files = dir.listFiles();
        if (files != null) for (File f : files) f.delete();
    }

    private String readUri(Uri uri) throws Exception {
        InputStream in = getContentResolver().openInputStream(uri);
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private void writeUri(Uri uri, String text) throws Exception {
        OutputStream os = getContentResolver().openOutputStream(uri);
        os.write(text.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();
    }

    private static String readFile(File f) throws Exception {
        InputStream in = new java.io.FileInputStream(f);
        BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private static void writeFile(File f, String text) throws Exception {
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(text.getBytes(StandardCharsets.UTF_8));
        fos.close();
    }

    private void section(String title) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(10), dp(10), dp(10), dp(10));
        head.setTag("sectionHead");
        head.setBackground(round(MiraTheme.PAGE_SURFACE, 8));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(10);
        head.setLayoutParams(hlp);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setTag("title");
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(tv);

        TextView arrow = new TextView(this);
        arrow.setText("▸");
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        arrow.setTag("arrow");
        head.addView(arrow);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(8));
        content.setVisibility(View.GONE);
        content.setTag(arrow);

        head.setOnClickListener(v -> toggleSection(content, arrow));
        block.addView(head);
        block.addView(content);
        root.addView(block);
        sectionContent = content;
        if (expandedContent == null) {
            content.setVisibility(View.VISIBLE);
            arrow.setText("▾");
            expandedContent = content;
        }
    }

    private void toggleSection(LinearLayout content, TextView arrow) {
        boolean show = content.getVisibility() != View.VISIBLE;
        if (show) {
            if (expandedContent != null && expandedContent != content) {
                expandedContent.setVisibility(View.GONE);
                Object old = expandedContent.getTag();
                if (old instanceof TextView) ((TextView) old).setText("▸");
            }
            content.setVisibility(View.VISIBLE);
            arrow.setText("▾");
            expandedContent = content;
        } else {
            content.setVisibility(View.GONE);
            arrow.setText("▸");
            if (expandedContent == content) expandedContent = null;
        }
    }

    private void addViewToSection(View v) {
        (sectionContent != null ? sectionContent : root).addView(v);
    }

    private void addViewToSection(View v, LinearLayout.LayoutParams lp) {
        (sectionContent != null ? sectionContent : root).addView(v, lp);
    }

    private TextView body(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTag("sub");
        tv.setLineSpacing(0, 1.25f);
        addViewToSection(tv);
        return tv;
    }

    private TextView button(String text, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTag("btn");
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(10), dp(8), dp(10), dp(8));
        tv.setOnClickListener(l);
        return tv;
    }

    private void rowButtons(String[] labels, View.OnClickListener[] ls) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(6);
        row.setLayoutParams(rlp);
        for (int i = 0; i < labels.length; i++) {
            TextView b = button(labels[i], ls[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
            lp.rightMargin = i < labels.length - 1 ? dp(8) : 0;
            b.setLayoutParams(lp);
            row.addView(b);
        }
        addViewToSection(row);
    }

    private void editRow(String label, String value, boolean secret, ValueSink sink) {
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        l.setTag("sub");
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        l.setLayoutParams(lp);
        addViewToSection(l);
        EditText et = new EditText(this);
        et.setText(value);
        et.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        et.setTextColor(MiraTheme.PAGE_INK);
        et.setBackground(round(MiraTheme.PAGE_SURFACE, 6));
        et.setPadding(dp(10), dp(6), dp(10), dp(6));
        if (secret) et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                sink.accept(s.toString());
            }
        });
        addViewToSection(et);
    }

    private void switchRow(String label, boolean checked, android.widget.CompoundButton.OnCheckedChangeListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(8);
        row.setLayoutParams(rlp);
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTag("ink");
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(tv);
        Switch sw = new Switch(this);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(l);
        row.addView(sw);
        addViewToSection(row);
    }

    private void applyPageColors() {
        if (root == null) return;
        root.setBackgroundColor(MiraTheme.PAGE_BG);
        applyColors(root);
        try {
            getWindow().setStatusBarColor(MiraTheme.PAGE_BG);
        } catch (Throwable ignored) {
        }
    }

    private void applyColors(View v) {
        Object tag = v.getTag();
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            if ("title".equals(tag)) t.setTextColor(MiraTheme.PAGE_TITLE);
            else if ("sub".equals(tag)) t.setTextColor(MiraTheme.PAGE_SUB);
            else if ("arrow".equals(tag)) t.setTextColor(MiraTheme.PAGE_SUB);
            else if ("btn".equals(tag)) {
                t.setTextColor(MiraTheme.PAGE_INK);
                t.setBackground(round(MiraTheme.PAGE_CHIP, 8));
            } else {
                t.setTextColor(MiraTheme.PAGE_INK);
            }
        }
        if ("sectionHead".equals(tag)) {
            v.setBackground(round(MiraTheme.PAGE_SURFACE, 8));
        }
        if (v instanceof Switch) {
            Switch sw = (Switch) v;
            sw.setThumbTintList(ColorStateList.valueOf(MiraTheme.PAGE_TITLE));
            sw.setTrackTintList(ColorStateList.valueOf(MiraTheme.PAGE_LINE));
        }
        if (v instanceof EditText) {
            ((EditText) v).setTextColor(MiraTheme.PAGE_INK);
            ((EditText) v).setHintTextColor(MiraTheme.PAGE_SUB);
            v.setBackground(round(MiraTheme.PAGE_SURFACE, 6));
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) applyColors(g.getChildAt(i));
        }
    }

    private GradientDrawable round(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String s) {
        try {
            Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private interface ValueSink {
        void accept(String value);
    }
}