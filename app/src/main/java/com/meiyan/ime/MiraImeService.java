package com.meiyan.ime;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.inputmethodservice.InputMethodService;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.meiyan.ime.ai.DeepSeekClient;
import com.meiyan.ime.engine.DictRepo;
import com.meiyan.ime.engine.MemoryStore;
import com.meiyan.ime.engine.PinyinEngine;
import com.meiyan.ime.engine.TouchModel;
import com.meiyan.ime.store.Prefs;
import com.meiyan.ime.theme.MiraTheme;
import com.meiyan.ime.voice.VoiceEngine;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MiraImeService extends InputMethodService {
    private static final String[] CN_PUNCT = {
            "，", "。", "！", "？", "、", "；", "：", "“", "”", "‘",
            "’", "（", "）", "《", "》", "【", "】", "…", "—", "～",
            "@", "#", "￥", "%", "&", "*", "＋", "－", "＝", "/"
    };
    private static final String[] EN_PUNCT = {
            ",", ".", "!", "?", ";", ":", "\"", "'", "(", ")",
            "[", "]", "{", "}", "<", ">", "/", "\\", "|", "~",
            "@", "#", "$", "%", "^", "&", "*", "+", "-", "="
    };
    private static final String[] EMOJI = {
            "😀", "😁", "😂", "🤣", "😊", "😍", "😘", "😎",
            "🤔", "😅", "😭", "😡", "🥺", "😴", "👍", "👎",
            "👏", "🙏", "💪", "🤝", "❤️", "💔", "🔥", "✨",
            "🎉", "🎁", "☀️", "🌙", "⭐", "🌈", "☕", "🍜",
            "🚗", "✈️", "🏠", "📱", "💻", "📷", "⚽", "🎵",
            "✅", "❌", "❓", "❗", "💯", "👌", "🤗", "😇"
    };
    private static final String[] NINE_LETTERS = {
            "abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz", "。"
    };

    private static volatile MiraImeService current;

    private Prefs prefs;
    private volatile DictRepo repo = new DictRepo();
    private final MemoryStore memory = new MemoryStore();
    private final TouchModel touch = new TouchModel();
    private final VoiceEngine voice = new VoiceEngine();
    private volatile PinyinEngine engine;

    private LinearLayout root;
    private LinearLayout aiPanel;
    private EditText aiEdit;
    private TextView aiHandle;
    private FrameLayout topBar;
    private LinearLayout toolRow;
    private LinearLayout pinyinRow;
    private TextView pinyinView;
    private TextView clearView;
    private HorizontalScrollView candScroll;
    private LinearLayout candRow;
    private FrameLayout kbWrap;
    private LinearLayout kbArea;
    private FrameLayout bubbleLayer;
    private TextView[] bubbles;

    private final StringBuilder composing = new StringBuilder();
    private List<String> candidates = new ArrayList<>();
    private boolean assocMode;
    private boolean chinese = true;
    private boolean password;
    private int imeAction = EditorInfo.IME_ACTION_NONE;
    private int shiftState;
    private int layoutMode;
    private int panelMode;
    private volatile boolean dictLoaded;
    private volatile boolean dictLoading;
    private long lastKeyTime;
    private long lastShiftTime;
    private long lastBackspaceTime;
    private char lastDeletedLetter;
    private long lastNineTime;
    private int lastBuiltAction = Integer.MIN_VALUE;
    private final java.util.HashSet<String> loadedDicts = new java.util.HashSet<>();
    private String clipPreview;
    private String clipFull;
    private long clipAt;
    private final ArrayDeque<String> logs = new ArrayDeque<>();
    private Runnable pendingLong;
    private View pendingLongView;
    private Runnable pendingRebuild;
    private Runnable pendingBackspace;
    private View pendingBackspaceView;
    private boolean layoutMenu;
    private ClipboardManager clipManager;
    private ClipboardManager.OnPrimaryClipChangedListener clipListener;

    private boolean aiMode;
    private String rwOriginal = "";
    private int rwOriginalLen;
    private int rwSeq;
    private int rwBusySeq;
    private final boolean[] rwBusy = new boolean[4];

    private AudioRecord recorder;
    private volatile boolean recording;
    private volatile boolean voiceStarted;
    private volatile short[] voiceData = new short[0];
    private volatile int voiceLen;
    private Thread recordThread;
    private volatile boolean voiceStopPending;
    private volatile boolean voicePreparing;
    private volatile boolean voiceNoPerm;
    private volatile String voicePreviewText = "";
    private boolean spaceUpFlag;
    private boolean spaceMoved;
    private boolean spaceLongFired;
    private float downX;
    private float downY;

    private final ArrayList<String> seqCandidates = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        current = this;
        prefs = new Prefs(this);
        MiraTheme.applyTheme(prefs.getInt(Prefs.THEME, MiraTheme.MIRA));
        MiraTheme.freqColor = prefs.getBool(Prefs.FREQ_COLOR, true);
        touch.setEnabled(prefs.getBool(Prefs.TOUCH_ON, true));
        engine = new PinyinEngine(repo, memory);
        engine.setFuzzyEnabled(prefs.getBool(Prefs.FUZZY, true));
        memory.load(prefs.getString(Prefs.MEMORY, ""));
        memory.loadEnglish(prefs.getString(Prefs.MEMORY_EN, ""));
        try {
            touch.importJson(prefs.getString(Prefs.TOUCH_JSON, ""));
        } catch (Throwable ignored) {
        }
        loadDict();
        new Thread(() -> voice.init(this), "voice-init").start();
        registerClipListener();
    }

    private void loadDict() {
        reloadDict(null);
    }

    static void reloadFromSettings() {
        MiraImeService s = current;
        if (s != null) s.reloadDictAsync();
    }

    private void reloadDictAsync() {
        reloadDict(() -> toast("词库已更新"));
    }

    private void reloadDict(Runnable after) {
        if (dictLoading) return;
        dictLoading = true;
        new Thread(() -> {
            try {
                DictRepo nr = new DictRepo();
                loadAssets(nr);
                applyUserWords(nr);
                loadImportedInto(nr);
                PinyinEngine ne = new PinyinEngine(nr, memory);
                ne.setFuzzyEnabled(prefs.getBool(Prefs.FUZZY, true));
                postUi(() -> {
                    repo = nr;
                    engine = ne;
                    dictLoaded = true;
                    try {
                        refreshCandidates();
                        updatePinyinRow();
                    } catch (Throwable ignored) {
                    }
                    log("词库加载完成：" + nr.statsText());
                    if (after != null) after.run();
                });
            } catch (Throwable t) {
                log("词库加载：未完成 " + t.getClass().getSimpleName());
            } finally {
                dictLoading = false;
            }
        }, "dict-load").start();
    }

    private void loadAssets(DictRepo r) throws IOException {
        r.load(getAssets().open("pinyin/word.txt"),
                getAssets().open("pinyin/abbr.txt"),
                getAssets().open("pinyin/single.txt"),
                getAssets().open("pinyin/assoc.txt"),
                getAssets().open("pinyin/poly.txt"),
                getAssets().open("pinyin/syll.txt"));
    }

    private void applyUserWords(DictRepo r) {
        for (String w : Prefs.splitPipe(prefs.getString(Prefs.USER_WORDS, ""), 500)) {
            if (!w.isEmpty()) r.addUserWord(w);
        }
    }

    private void loadImportedInto(DictRepo r) {
        try {
            File dir = new File(getFilesDir(), "dicts");
            File[] fs = dir.listFiles();
            if (fs == null) return;
            for (File f : fs) {
                String text = readTextFile(f);
                if (text != null && !text.isEmpty()) r.importText(text);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public View onCreateInputView() {
        try {
            buildRoot();
            return root;
        } catch (Throwable t) {
            log("布局兜底：" + t.getClass().getSimpleName());
            if (root != null && topBar != null && candRow != null) {
                try {
                    fillFallbackKeyboard();
                    return root;
                } catch (Throwable ignored) {
                }
            }
            try {
                return buildFallback();
            } catch (Throwable t2) {
                TextView tv = new TextView(this);
                tv.setText("米拉之家");
                tv.setGravity(Gravity.CENTER);
                tv.setOnClickListener(v -> {
                    try {
                        commitTextSafe(" ");
                    } catch (Throwable ignored) {
                    }
                });
                return tv;
            }
        }
    }

    private void fillFallbackKeyboard() {
        if (root == null) return;
        if (kbWrap == null || kbArea == null) {
            kbWrap = new FrameLayout(this);
            kbWrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(234)));
            kbArea = new LinearLayout(this);
            kbArea.setOrientation(LinearLayout.VERTICAL);
            kbWrap.addView(kbArea, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            bubbleLayer = new FrameLayout(this);
            kbWrap.addView(bubbleLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(kbWrap);
            try {
                buildBubbles();
            } catch (Throwable ignored) {
            }
        }
        kbArea.removeAllViews();
        TextView tv = new TextView(this);
        tv.setText("米拉之家 · 点此输入空格");
        tv.setTextColor(MiraTheme.KEY_TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(234)));
        tv.setOnClickListener(v -> commitTextSafe(" "));
        kbArea.addView(tv);
    }

    private View buildFallback() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(MiraTheme.KB_BG);
        TextView tv = new TextView(this);
        tv.setText("米拉之家 · 点此输入空格");
        tv.setTextColor(MiraTheme.KEY_TEXT);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));
        tv.setOnClickListener(v -> {
            try {
                commitTextSafe(" ");
            } catch (Throwable ignored) {
            }
        });
        box.addView(tv);
        return box;
    }

    private void buildRoot() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(MiraTheme.KB_BG);
        root.setMinimumHeight(dp(320));
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        aiPanel = buildAiPanel();
        root.addView(aiPanel);

        topBar = new FrameLayout(this);
        topBar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40)));
        toolRow = buildToolRow();
        topBar.addView(toolRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        pinyinRow = buildPinyinRow();
        topBar.addView(pinyinRow, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(topBar);

        candScroll = new HorizontalScrollView(this);
        candScroll.setHorizontalScrollBarEnabled(false);
        candScroll.setFillViewport(true);
        candScroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        candScroll.setBackgroundColor(MiraTheme.KB_BG);
        candRow = new LinearLayout(this);
        candRow.setOrientation(LinearLayout.HORIZONTAL);
        candRow.setGravity(Gravity.CENTER_VERTICAL);
        candRow.setPadding(dp(8), 0, dp(8), 0);
        candScroll.addView(candRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(candScroll);

        kbWrap = new FrameLayout(this);
        kbWrap.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(234)));
        kbArea = new LinearLayout(this);
        kbArea.setOrientation(LinearLayout.VERTICAL);
        kbWrap.addView(kbArea, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        bubbleLayer = new FrameLayout(this);
        kbWrap.addView(bubbleLayer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(kbWrap);
        try {
            buildBubbles();
        } catch (Throwable ignored) {
        }

        try {
            rebuildKeyboard();
        } catch (Throwable t) {
            log("键盘重建兜底：" + t.getClass().getSimpleName());
            fillFallbackKeyboard();
        }
        try {
            updatePinyinRow();
            refreshCandidates();
        } catch (Throwable ignored) {
        }
    }

    private LinearLayout buildToolRow() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(6), 0, dp(6), 0);

        TextView logo = new TextView(this);
        logo.setText("言");
        logo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        logo.setTextColor(MiraTheme.ICON_TEXT);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(bg(MiraTheme.ICON_BG, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(30), dp(28));
        logo.setLayoutParams(lp);
        logo.setOnClickListener(v -> {
            String msg = "米拉之家 v" + versionName() + "\n" + repo.statsText();
            toast(msg);
            log("版本 " + versionName() + " " + repo.statsText());
        });
        logo.setOnLongClickListener(v -> {
            showLogs();
            return true;
        });
        bar.addView(logo);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        bar.addView(spacer);

        bar.addView(toolButton("⌨", v -> {
            panelMode = 0;
            layoutMenu = true;
            seqCandidates.clear();
            renderCandidates();
            toast("选择布局");
        }));
        bar.addView(toolButton("语", v -> {
            layoutMenu = false;
            seqCandidates.clear();
            panelMode = 3;
            rebuildKeyboard();
            toast("常用语");
        }));
        bar.addView(toolButton("脸", v -> {
            layoutMenu = false;
            seqCandidates.clear();
            panelMode = 2;
            rebuildKeyboard();
            toast("表情");
        }));
        bar.addView(toolButton("贴", v -> {
            layoutMenu = false;
            seqCandidates.clear();
            panelMode = 4;
            rebuildKeyboard();
            toast("剪贴板");
        }));
        bar.addView(toolButton("⌄", v -> {
            layoutMenu = false;
            requestHideSelf(0);
        }));
        return bar;
    }

    private String layoutName() {
        if (layoutMode == 1) return "九键";
        if (layoutMode == 2) return "手写";
        return "全键盘";
    }

    private TextView toolButton(String label, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        tv.setTextColor(MiraTheme.KEY_TEXT);
        tv.setGravity(Gravity.CENTER);
        tv.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(36)));
        tv.setOnClickListener(l);
        return tv;
    }

    private LinearLayout buildPinyinRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(8), 0);
        row.setBackgroundColor(MiraTheme.KB_BG);
        pinyinView = new TextView(this);
        pinyinView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        pinyinView.setTextColor(MiraTheme.CARD_SUB);
        pinyinView.setSingleLine(true);
        pinyinView.setEllipsize(TextUtils.TruncateAt.START);
        pinyinView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(pinyinView);
        clearView = new TextView(this);
        clearView.setText("✕");
        clearView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        clearView.setTextColor(MiraTheme.CARD_SUB);
        clearView.setGravity(Gravity.CENTER);
        clearView.setLayoutParams(new LinearLayout.LayoutParams(dp(36), ViewGroup.LayoutParams.MATCH_PARENT));
        clearView.setOnClickListener(v -> {
            composing.setLength(0);
            assocMode = false;
            candidates.clear();
            refreshCandidates();
            updatePinyinRow();
        });
        row.addView(clearView);
        row.setVisibility(View.GONE);
        return row;
    }

    private void rebuildKeyboard() {
        try {
            clearPendingBackspace();
            if (pendingLong != null) {
                View v = pendingLongView != null ? pendingLongView : kbArea;
                if (v != null) v.removeCallbacks(pendingLong);
                pendingLong = null;
                pendingLongView = null;
            }
            MiraTheme.maxTypingCount = Math.max(12, touch.maxCount());
            kbArea.removeAllViews();
            bubbleLayer.setAlpha(1f);
            hideBubbles();
            refreshBubbles();
            if (panelMode != 0) {
                if (panelMode == 1) buildSymbolPanel();
                else if (panelMode == 2) buildEmojiPanel();
                else if (panelMode == 3) buildPhrasePanel();
                else buildClipPanel();
            } else if (layoutMode == 1) {
                buildNine();
            } else if (layoutMode == 2) {
                buildHand();
            } else {
                buildFull();
            }
        } catch (Throwable t) {
            log("键盘重建：" + t.getClass().getSimpleName());
            fillFallbackKeyboard();
        }
    }

    private void rebuildKeyboardSoon() {
        if (root == null) return;
        if (pendingRebuild != null) root.removeCallbacks(pendingRebuild);
        long dt = System.currentTimeMillis() - lastKeyTime;
        long delay = dt < 400 ? 400 - dt : 0;
        pendingRebuild = () -> {
            pendingRebuild = null;
            try {
                rebuildKeyboard();
                updatePinyinRow();
                refreshCandidates();
            } catch (Throwable ignored) {
            }
        };
        root.postDelayed(pendingRebuild, delay);
    }

    private void selectLayout(int mode) {
        layoutMode = mode;
        panelMode = 0;
        layoutMenu = false;
        seqCandidates.clear();
        renderCandidates();
        toast("布局：" + layoutName());
        rebuildKeyboardSoon();
        updatePinyinRow();
    }

    private void buildFull() {
        addLetterRow("qwertyuiop", false);
        addLetterRow("asdfghjkl", true);
        LinearLayout r3 = baseRow();
        r3.addView(makeShiftKey(1f), weight(1f));
        String third = "zxcvbnm";
        for (int i = 0; i < third.length(); i++) r3.addView(makeLetter(third.charAt(i), 1f));
        r3.addView(makeFunc("⌫", 1f, v -> onBackspace()), weight(1f));
        r3.getChildAt(r3.getChildCount() - 1).setOnTouchListener(backspaceTouch());
        kbArea.addView(r3);
        addBottomRow(false);
    }

    private TextView makeShiftKey(float weight) {
        TextView tv = makeFunc(shiftLabel(), weight, v -> cycleShift());
        if (shiftState == 2) {
            tv.setText("⇧锁");
            tv.setTextColor(MiraTheme.ACCENT_TEXT);
            tv.setBackground(bg(MiraTheme.ACCENT, 8));
        } else if (shiftState == 1) {
            tv.setText("⇧");
            tv.setTextColor(MiraTheme.SHIFT_TEMP_I);
            tv.setBackground(bg(MiraTheme.SHIFT_TEMP_BG, 8));
        }
        return tv;
    }

    private void addLetterRow(String letters, boolean pad) {
        LinearLayout row = baseRow();
        if (pad) row.addView(spacer(0.5f));
        for (int i = 0; i < letters.length(); i++) row.addView(makeLetter(letters.charAt(i), 1f));
        if (pad) row.addView(spacer(0.5f));
        kbArea.addView(row);
    }

    private LinearLayout baseRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        row.setPadding(dp(2), dp(1), dp(2), dp(1));
        return row;
    }

    private LinearLayout panelBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return bar;
    }

    private View spacer(float weight) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
        return v;
    }

    private TextView makeLetter(char c, float weight) {
        TextView tv = new TextView(this);
        String up = String.valueOf(c).toUpperCase();
        tv.setText(up);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        tv.setTextColor(MiraTheme.KEY_TEXT);
        tv.setGravity(Gravity.CENTER);
        int color = MiraTheme.freqColorFor(touch.countOf(c), c);
        GradientDrawable base = bg(color, 8);
        float[] off = touch.offsetOf(c);
        if (Math.abs(off[0]) > 0.01f || Math.abs(off[1]) > 0.01f) {
            GradientDrawable hot = new GradientDrawable();
            hot.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            hot.setGradientRadius(dp(26));
            hot.setGradientCenter(0.5f + off[0] * 0.6f, 0.5f + off[1] * 0.6f);
            hot.setColors(new int[]{0x22000000, 0x00000000});
            hot.setCornerRadius(dp(8));
            tv.setBackground(new LayerDrawable(new GradientDrawable[]{base, hot}));
        } else {
            tv.setBackground(base);
        }
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
        tv.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                lastKeyTime = System.currentTimeMillis();
                v.setAlpha(0.6f);
                float nx = v.getWidth() > 0 ? e.getX() / v.getWidth() : 0.5f;
                float ny = v.getHeight() > 0 ? e.getY() / v.getHeight() : 0.5f;
                touch.record(c, nx, ny);
                inputLetter(c);
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
                return true;
            }
            return false;
        });
        return tv;
    }

    private TextView makeFunc(String label, float weight, View.OnClickListener l) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(MiraTheme.FUNC_TEXT);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(bg(MiraTheme.FUNC, 8));
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
        tv.setOnClickListener(l);
        return tv;
    }

    private LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w);
    }

    private void addBottomRow(boolean nine) {
        LinearLayout row = baseRow();
        row.addView(makeFunc("123", 1f, v -> {
            panelMode = 1;
            rebuildKeyboard();
        }));
        row.addView(makeCommaKey(1f));
        TextView space = spaceKey(2.6f);
        row.addView(space);
        row.addView(makeFunc(chinese ? "中" : "英", 1.05f, v -> {
            chinese = !chinese;
            composing.setLength(0);
            assocMode = false;
            candidates.clear();
            seqCandidates.clear();
            layoutMenu = false;
            toast(chinese ? "中文输入" : "英文输入");
            rebuildKeyboardSoon();
            refreshCandidates();
            updatePinyinRow();
        }));
        row.addView(makeFunc(enterLabel(), 1.35f, v -> onEnter()));
        kbArea.addView(row);
    }

    private void buildNine() {
        for (int i = 0; i < 9; i++) {
            if (i % 3 == 0) {
                LinearLayout row = baseRow();
                kbArea.addView(row);
            }
            LinearLayout row = (LinearLayout) kbArea.getChildAt(kbArea.getChildCount() - 1);
            row.addView(makeNineKey(i));
        }
        LinearLayout row = baseRow();
        row.addView(makeFunc("符", 1f, v -> {
            panelMode = 1;
            rebuildKeyboard();
        }));
        row.addView(spaceKey(2.2f));
        row.addView(makeFunc(chinese ? "中英" : "英中", 1.15f, v -> {
            chinese = !chinese;
            composing.setLength(0);
            assocMode = false;
            candidates.clear();
            seqCandidates.clear();
            layoutMenu = false;
            toast(chinese ? "中文输入" : "英文输入");
            rebuildKeyboardSoon();
            refreshCandidates();
            updatePinyinRow();
        }));
        TextView del = makeFunc("⌫", 1.2f, v -> onBackspace());
        del.setOnTouchListener(backspaceTouch());
        row.addView(del);
        kbArea.addView(row);
    }

    private TextView makeNineKey(int i) {
        final String letters = NINE_LETTERS[i];
        TextView tv = new TextView(this);
        tv.setText((i + 1) + "\n" + letters);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextColor(MiraTheme.KEY_TEXT);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(bg(MiraTheme.KEY, 8));
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        tv.setOnTouchListener((v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                lastKeyTime = System.currentTimeMillis();
                v.setAlpha(0.6f);
                ninePress(letters);
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
                return true;
            }
            return false;
        });
        return tv;
    }

    private void ninePress(String letters) {
        seqCandidates.clear();
        layoutMenu = false;
        if (!chinese) {
            log("九键 " + letters.charAt(0));
            commitTextSafe(String.valueOf(letters.charAt(0)));
            return;
        }
        long now = System.currentTimeMillis();
        int idx = 0;
        if (now - lastNineTime < 900 && composing.length() > 0) {
            char last = composing.charAt(composing.length() - 1);
            int p = letters.indexOf(last);
            if (p >= 0) {
                idx = (p + 1) % letters.length();
                composing.setLength(composing.length() - 1);
            }
        }
        composing.append(letters.charAt(idx));
        lastNineTime = now;
        log("九键 " + letters.charAt(idx));
        refreshCandidates();
        updatePinyinRow();
    }

    private void buildHand() {
        HandView hv = new HandView(this);
        hv.setBackgroundColor(MiraTheme.CARD_CELL);
        hv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        kbArea.addView(hv);
        LinearLayout row = baseRow();
        row.addView(makeFunc("清除", 1.4f, v -> hv.clear()));
        row.addView(spaceKey(2f));
        TextView del = makeFunc("⌫", 1.2f, v -> onBackspace());
        del.setOnTouchListener(backspaceTouch());
        row.addView(del);
        kbArea.addView(row);
    }

    private void buildSymbolPanel() {
        String[] punct = chinese ? CN_PUNCT : EN_PUNCT;
        LinearLayout row = baseRow();
        String digits = "1234567890";
        for (int i = 0; i < 10; i++) {
            final String n = String.valueOf(digits.charAt(i));
            row.addView(makeFunc(n, 1f, v -> inputSymbolNumber(n)));
        }
        kbArea.addView(row);
        for (int r = 0; r < 3; r++) {
            LinearLayout pr = baseRow();
            for (int c = 0; c < 10; c++) {
                final String s = punct[r * 10 + c];
                pr.addView(makeFunc(s, 1f, v -> handlePunct(s)));
            }
            kbArea.addView(pr);
        }
        LinearLayout row4 = baseRow();
        row4.addView(makeFunc("⌨", 1f, v -> {
            panelMode = 0;
            layoutMenu = false;
            rebuildKeyboard();
        }));
        row4.addView(spaceKey(2.6f));
        TextView del = makeFunc("⌫", 1.2f, v -> onBackspace());
        del.setOnTouchListener(backspaceTouch());
        row4.addView(del);
        row4.addView(makeFunc(chinese ? "中" : "英", 1f, v -> {
            chinese = !chinese;
            layoutMenu = false;
            seqCandidates.clear();
            toast(chinese ? "中文标点" : "英文标点");
            rebuildKeyboardSoon();
            refreshCandidates();
            updatePinyinRow();
        }));
        kbArea.addView(row4);
    }

    private void handlePunct(String s) {
        if (aiMode) {
            log("改写标点 " + s);
            commitTextSafe(s);
            return;
        }
        log("标点 " + s);
        commitTextSafe(s);
    }

    private void inputSymbolNumber(String n) {
        if (chinese && composing.length() > 0) {
            composing.append(n);
            seqCandidates.clear();
            layoutMenu = false;
            log("编码 " + n);
            refreshCandidates();
            updatePinyinRow();
        } else {
            commitTextSafe(n);
        }
    }

    private void buildEmojiPanel() {
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < EMOJI.length; i++) {
            if (i % 8 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            final String em = EMOJI[i];
            TextView tv = new TextView(this);
            tv.setText(em);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
            tv.setGravity(Gravity.CENTER);
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, dp(44), 1f));
            tv.setOnClickListener(v -> commitTextSafe(em));
            row.addView(tv);
        }
        scroll.addView(grid);
        kbArea.addView(scroll);
        LinearLayout bar = panelBar();
        bar.addView(makeFunc("⌨返回键盘", 2f, v -> {
            panelMode = 0;
            rebuildKeyboard();
        }));
        TextView del = makeFunc("⌫", 1f, v -> onBackspace());
        del.setOnTouchListener(backspaceTouch());
        bar.addView(del);
        kbArea.addView(bar);
    }

    private void buildPhrasePanel() {
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        for (final String p : prefs.phraseList()) {
            TextView tv = new TextView(this);
            tv.setText(p);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            tv.setTextColor(MiraTheme.KEY_TEXT);
            tv.setPadding(dp(14), dp(10), dp(14), dp(10));
            tv.setOnClickListener(v -> commitTextSafe(p));
            tv.setOnLongClickListener(v -> {
                List<String> list = prefs.phraseList();
                list.remove(p);
                prefs.savePhrases(list);
                toast("已删除常用语");
                rebuildKeyboard();
                return true;
            });
            col.addView(tv);
        }
        scroll.addView(col);
        kbArea.addView(scroll);
        LinearLayout bar = panelBar();
        bar.addView(makeFunc("＋添加", 1f, v -> addPhraseDialog()));
        bar.addView(makeFunc("⌨返回", 1f, v -> {
            panelMode = 0;
            rebuildKeyboard();
        }));
        TextView del = makeFunc("⌫", 1f, v -> onBackspace());
        del.setOnTouchListener(backspaceTouch());
        bar.addView(del);
        kbArea.addView(bar);
    }

    private void addPhraseDialog() {
        try {
            final EditText et = new EditText(this);
            et.setHint("输入常用语");
            new AlertDialog.Builder(this)
                    .setTitle("添加常用语")
                    .setView(et)
                    .setPositiveButton("保存", (d, w) -> {
                        String s = et.getText().toString().trim();
                        if (s.isEmpty()) return;
                        List<String> list = prefs.phraseList();
                        list.add(0, s);
                        prefs.savePhrases(list);
                        toast("已添加");
                        rebuildKeyboard();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        } catch (Throwable t) {
            toast("添加失败");
        }
    }

    private void buildClipPanel() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        try {
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData cd = cm.getPrimaryClip();
                if (cd != null && cd.getItemCount() > 0) {
                    CharSequence cs = cd.getItemAt(0).coerceToText(this);
                    if (cs != null) prefs.addClip(cs.toString());
                }
            }
        } catch (Throwable ignored) {
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        List<String> list = prefs.clipList();
        if (list.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("暂无剪贴内容");
            empty.setTextColor(MiraTheme.CARD_SUB);
            empty.setPadding(dp(14), dp(12), dp(14), dp(12));
            col.addView(empty);
        }
        for (final String s : list) {
            TextView tv = new TextView(this);
            tv.setText(preview(s, 24));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            tv.setTextColor(MiraTheme.KEY_TEXT);
            tv.setPadding(dp(14), dp(10), dp(14), dp(10));
            tv.setOnClickListener(v -> commitTextSafe(s));
            col.addView(tv);
        }
        scroll.addView(col);
        kbArea.addView(scroll);
        LinearLayout bar = panelBar();
        bar.addView(makeFunc("⌨返回", 2f, v -> {
            panelMode = 0;
            rebuildKeyboard();
        }));
        TextView del = makeFunc("⌫", 1f, v -> onBackspace());
        del.setOnTouchListener(backspaceTouch());
        bar.addView(del);
        kbArea.addView(bar);
    }

    private static String preview(String s, int n) {
        String t = s.replace('\n', ' ');
        return t.length() <= n ? t : t.substring(0, n) + "…";
    }

    private LinearLayout buildAiPanel() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(MiraTheme.RW_GLASS);
        box.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(210)));
        box.setVisibility(View.GONE);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(10), dp(4), dp(10), 0);
        TextView title = new TextView(this);
        title.setText("AI 文段改写");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        title.setTextColor(MiraTheme.RW_INK);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        head.addView(title);
        aiHandle = new TextView(this);
        aiHandle.setText("⌄ 回填");
        aiHandle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        aiHandle.setTextColor(MiraTheme.RW_BTN_ON);
        aiHandle.setPadding(dp(8), dp(2), dp(8), dp(2));
        aiHandle.setOnClickListener(v -> applyAiText());
        head.addView(aiHandle);
        box.addView(head);

        aiEdit = new EditText(this);
        aiEdit.setBackgroundColor(MiraTheme.CARD_CELL);
        aiEdit.setTextColor(MiraTheme.RW_INK);
        aiEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        aiEdit.setGravity(Gravity.TOP | Gravity.START);
        aiEdit.setPadding(dp(10), dp(6), dp(10), dp(6));
        aiEdit.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        aiEdit.setFocusable(false);
        aiEdit.setFocusableInTouchMode(false);
        aiEdit.setClickable(false);
        aiEdit.setLongClickable(false);
        aiEdit.setCursorVisible(false);
        box.addView(aiEdit);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(4), dp(6), dp(4));
        List<String> names = Prefs.splitPipe(prefs.getString(Prefs.AI_NAMES, Prefs.DEFAULT_NAMES), 4);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView b = new TextView(this);
            b.setText(names.get(i).isEmpty() ? "改写" : names.get(i));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            b.setTextColor(MiraTheme.RW_BTN_INK);
            b.setGravity(Gravity.CENTER);
            b.setBackground(bg(MiraTheme.RW_BTN_BG, 8));
            b.setLayoutParams(new LinearLayout.LayoutParams(0, dp(38), 1f));
            b.setOnClickListener(v -> runAiRewrite(idx));
            row.addView(b);
        }
        box.addView(row);
        return box;
    }

    private void onAiBeautify() {
        if (aiMode) {
            closeAi();
            return;
        }
        if (password) {
            toast("密码输入不启用改写");
            return;
        }
        try {
            InputConnection ic = getCurrentInputConnection();
            CharSequence before = ic == null ? null : ic.getTextBeforeCursor(600, 0);
            String raw = before == null ? "" : before.toString();
            int nl = raw.lastIndexOf('\n');
            if (nl >= 0) raw = raw.substring(nl + 1);
            rwOriginal = raw;
            rwOriginalLen = raw.length();
            if (rwOriginal.trim().isEmpty()) {
                CharSequence sel = ic == null ? null : ic.getSelectedText(0);
                if (sel != null && sel.length() > 0) {
                    rwOriginal = sel.toString();
                    rwOriginalLen = 0;
                }
            }
            rwSeq++;
            rwBusySeq = 0;
            aiMode = true;
            aiPanel.setVisibility(View.VISIBLE);
            aiEdit.setText(rwOriginal);
            aiEdit.setSelection(aiEdit.length());
            toast("已锁定原文，可继续人工编辑");
            log("AI 改写打开");
        } catch (Throwable t) {
            aiMode = false;
            aiPanel.setVisibility(View.GONE);
            log("AI 打开：" + t.getClass().getSimpleName());
        }
    }

    private void closeAi() {
        aiMode = false;
        rwSeq++;
        rwOriginal = "";
        rwOriginalLen = 0;
        aiPanel.setVisibility(View.GONE);
        toast("已收起改写面板");
    }

    private void applyAiText() {
        try {
            String text = aiEdit.getText().toString();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null && rwOriginalLen > 0) {
                try {
                    ic.deleteSurroundingText(rwOriginalLen, 0);
                } catch (Throwable ignored) {
                }
            }
            commitRaw(text);
            aiMode = false;
            rwSeq++;
            rwOriginal = "";
            rwOriginalLen = 0;
            aiPanel.setVisibility(View.GONE);
            toast("已回填到输入框");
        } catch (Throwable t) {
            toast("回填失败");
        }
    }

    private void runAiRewrite(int idx) {
        if (rwBusy[idx]) return;
        rwBusy[idx] = true;
        final int seq = ++rwBusySeq;
        final String source = rwOriginal;
        List<String> prompts = Prefs.splitPipe(prefs.getString(Prefs.AI_PROMPTS, Prefs.DEFAULT_PROMPTS), 4);
        List<String> docs = Prefs.splitPipe(prefs.getString(Prefs.AI_DOCS, Prefs.DEFAULT_DOCS), 4);
        final String prompt = prompts.get(idx);
        final String doc = docs.get(idx);
        toast("正在改写…");
        new Thread(() -> {
            DeepSeekClient.Result r = DeepSeekClient.rewriteText(MiraImeService.this, prompt, source, doc);
            postUi(() -> {
                rwBusy[idx] = false;
                if (!aiMode || seq != rwBusySeq || rwSeq <= 0) return;
                if (r.ok && r.text != null && !r.text.isEmpty()) {
                    aiEdit.setText(r.text);
                    aiEdit.setSelection(aiEdit.length());
                    toast("改写完成");
                } else {
                    toast(r.error == null ? "改写失败" : r.error);
                }
            });
        }, "ai-rewrite").start();
    }

    private void aiInsert(String s) {
        if (s == null || s.isEmpty()) return;
        int st = aiEdit.getSelectionStart();
        int len = aiEdit.length();
        if (st < 0 || st > len) st = len;
        aiEdit.getText().insert(st, s);
        aiEdit.setSelection(Math.min(aiEdit.length(), st + s.length()));
    }

    private void buildBubbles() {
        bubbles = new TextView[3];
        String[] labels = chinese ? new String[]{"。", "！", "？"} : new String[]{".", "!", "?"};
        for (int i = 0; i < 3; i++) {
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            tv.setTextColor(MiraTheme.BUBBLE_TEXT);
            tv.setGravity(Gravity.CENTER);
            tv.setBackground(bg(MiraTheme.BUBBLE_BG, 12));
            tv.setAlpha(0f);
            tv.setClickable(false);
            tv.setFocusable(false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(46), dp(46));
            tv.setLayoutParams(lp);
            tv.setTag(i);
            bubbleLayer.addView(tv);
            bubbles[i] = tv;
        }
    }

    private TextView makeCommaKey(float weight) {
        TextView tv = makeFunc(chinese ? "，" : ",", weight, null);
        final String[] labels = chinese ? new String[]{"。", "！", "？"} : new String[]{".", "!", "?"};
        final String base = chinese ? "，" : ",";
        tv.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastKeyTime = System.currentTimeMillis();
                    v.setAlpha(0.6f);
                    downX = e.getRawX();
                    downY = e.getRawY();
                    spaceMoved = false;
                    clearPendingLong();
                    pendingLongView = v;
                    pendingLong = () -> showBubbles(v, labels);
                    v.postDelayed(pendingLong, 320);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (bubblesVisible()) {
                        highlightBubble(e.getRawX(), e.getRawY());
                    } else if (Math.abs(e.getRawX() - downX) > dp(30)
                            || Math.abs(e.getRawY() - downY) > dp(30)) {
                        spaceMoved = true;
                        clearPendingLong();
                        hideBubbles();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    v.setAlpha(1f);
                    clearPendingLong();
                    if (bubblesVisible()) {
                        String pick = bubbleAt(e.getRawX(), e.getRawY());
                        hideBubbles();
                        handlePunct(pick == null ? base : pick);
                    } else if (!spaceMoved) {
                        handlePunct(base);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1f);
                    clearPendingLong();
                    hideBubbles();
                    return true;
                default:
                    return false;
            }
        });
        return tv;
    }

    private void showBubbles(View key, String[] labels) {
        try {
            int[] loc = new int[2];
            key.getLocationInWindow(loc);
            int[] wrapLoc = new int[2];
            kbWrap.getLocationInWindow(wrapLoc);
            float cx = loc[0] - wrapLoc[0] + key.getWidth() / 2f;
            float topY = loc[1] - wrapLoc[1] - dp(50);
            for (int i = 0; i < 3; i++) {
                TextView b = bubbles[i];
                b.setText(labels[i]);
                b.setX(cx - dp(46) * 1.5f + i * dp(46) + dp(4));
                b.setY(topY);
                b.setAlpha(1f);
                b.setTag(labels[i]);
            }
            spaceMoved = false;
        } catch (Throwable ignored) {
        }
    }

    private boolean bubblesVisible() {
        return bubbles != null && bubbles[0].getAlpha() > 0.5f;
    }

    private void highlightBubble(float rawX, float rawY) {
        if (!bubblesVisible()) return;
        String hit = bubbleAt(rawX, rawY);
        for (TextView b : bubbles) {
            boolean on = hit != null && hit.equals(b.getTag());
            b.setBackground(bg(on ? MiraTheme.RW_BTN_ON : MiraTheme.BUBBLE_BG, 12));
            b.setTextColor(on ? MiraTheme.RW_BTN_ON_I : MiraTheme.BUBBLE_TEXT);
        }
    }

    private String bubbleAt(float rawX, float rawY) {
        try {
            for (TextView b : bubbles) {
                int[] loc = new int[2];
                b.getLocationInWindow(loc);
                if (rawX >= loc[0] && rawX <= loc[0] + b.getWidth()
                        && rawY >= loc[1] && rawY <= loc[1] + b.getHeight()) {
                    return (String) b.getTag();
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void hideBubbles() {
        if (bubbles == null) return;
        for (TextView b : bubbles) b.setAlpha(0f);
    }

    private void clearPendingLong() {
        if (pendingLong != null) {
            View v = pendingLongView != null ? pendingLongView : kbArea;
            if (v != null) v.removeCallbacks(pendingLong);
        }
        pendingLong = null;
        pendingLongView = null;
    }

    private void refreshBubbles() {
        if (bubbles == null) return;
        String[] labels = chinese ? new String[]{"。", "！", "？"} : new String[]{".", "!", "?"};
        for (int i = 0; i < bubbles.length && i < labels.length; i++) {
            TextView b = bubbles[i];
            b.setText(labels[i]);
            b.setTextColor(MiraTheme.BUBBLE_TEXT);
            b.setBackground(bg(MiraTheme.BUBBLE_BG, 12));
            b.setAlpha(0f);
        }
    }

    private TextView spaceKey(float weight) {
        TextView tv = new TextView(this);
        tv.setText(chinese ? "≋ 空格" : "空格");
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(MiraTheme.FUNC_TEXT);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(bg(MiraTheme.FUNC, 8));
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight));
        tv.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastKeyTime = System.currentTimeMillis();
                    v.setAlpha(0.6f);
                    downX = e.getRawX();
                    downY = e.getRawY();
                    spaceMoved = false;
                    spaceLongFired = false;
                    spaceUpFlag = false;
                    clearPendingLong();
                    pendingLongView = v;
                    pendingLong = () -> {
                        spaceLongFired = true;
                        startVoice();
                    };
                    v.postDelayed(pendingLong, 250);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX;
                    float dy = e.getRawY() - downY;
                    if (Math.abs(dx) > dp(36) && Math.abs(dx) > 1.4f * Math.abs(dy)) {
                        spaceMoved = true;
                        spaceUpFlag = false;
                        clearPendingLong();
                    }
                    if (!voiceStarted && !spaceLongFired && dy < -dp(44)
                            && Math.abs(dy) > 1.3f * Math.abs(dx)) {
                        spaceUpFlag = true;
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    v.setAlpha(1f);
                    clearPendingLong();
                    if (spaceUpFlag && !spaceLongFired && !voiceStarted) {
                        spaceUpFlag = false;
                        onAiBeautify();
                    } else if (voiceStarted || voiceStopPending) {
                        stopVoice();
                    } else if (!spaceMoved && !spaceLongFired) {
                        onSpaceTap();
                    }
                    spaceUpFlag = false;
                    spaceMoved = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1f);
                    clearPendingLong();
                    spaceMoved = false;
                    spaceUpFlag = false;
                    spaceLongFired = false;
                    if (voiceStarted || voiceStopPending) stopVoice();
                    return true;
                default:
                    return false;
            }
        });
        return tv;
    }

    private void onSpaceTap() {
        seqCandidates.clear();
        layoutMenu = false;
        if (aiMode) {
            clearComposingRow();
            aiInsert(" ");
            return;
        }
        log("空格");
        if (composing.length() > 0 && !candidates.isEmpty()) {
            commitCandidate(0);
        } else {
            commitTextSafe(" ");
        }
    }

    private void startVoice() {
        if (recording) return;
        if (password) {
            toast("密码输入不启用语音");
            return;
        }
        if (!voice.isReady()) {
            voiceStopPending = true;
            voicePreparing = !voice.isFailed();
            voiceNoPerm = false;
            toast(voice.statusForRow());
            updatePinyinRow();
            log("语音：" + voice.status());
            return;
        }
        if (!hasAudioPermission()) {
            voiceStopPending = false;
            voicePreparing = false;
            voiceNoPerm = true;
            toast("请在设置页授予麦克风权限");
            updatePinyinRow();
            postUiDelayed(() -> {
                voiceNoPerm = false;
                updatePinyinRow();
            }, 4000);
            return;
        }
        try {
            voicePreparing = false;
            voiceNoPerm = false;
            voicePreviewText = "";
            int rate = 16000;
            voiceData = new short[rate * 11];
            voiceLen = 0;
            int min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            final AudioRecord rec = new AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, rate * 2));
            recorder = rec;
            rec.startRecording();
            recording = true;
            voiceStarted = true;
            final long startAt = System.currentTimeMillis();
            recordThread = new Thread(() -> {
                long lastPreview = 0;
                while (recording) {
                    int space = voiceData.length - voiceLen;
                    if (space <= 0) break;
                    int n;
                    try {
                        n = rec.read(voiceData, voiceLen, Math.min(1600, space));
                    } catch (Throwable t) {
                        break;
                    }
                    if (n > 0) voiceLen += n;
                    else if (n < 0) break;
                    long now = System.currentTimeMillis();
                    if (voiceLen >= 3200 && now - lastPreview > 800) {
                        lastPreview = now;
                        short[] copy = Arrays.copyOf(voiceData, voiceLen);
                        String p = voice.recognize(copy, 16000);
                        if (p != null && !p.isEmpty()) {
                            voicePreviewText = p;
                            postUi(MiraImeService.this::updatePinyinRow);
                        }
                    }
                    if (now - startAt > 10000) {
                        postUi(MiraImeService.this::stopVoice);
                        break;
                    }
                }
            }, "voice-record");
            recordThread.start();
            updatePinyinRow();
            log("语音开始录音");
        } catch (Throwable t) {
            recording = false;
            voiceStarted = false;
            toast("录音不可用，请检查麦克风权限");
            updatePinyinRow();
        }
    }

    private boolean hasAudioPermission() {
        try {
            return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return true;
        }
    }

    private void stopVoice() {
        if (voiceStopPending) {
            voiceStopPending = false;
            voiceStarted = false;
            voicePreparing = false;
            toast(voice.statusForRow());
            updatePinyinRow();
            return;
        }
        if (!recording && !voiceStarted) return;
        recording = false;
        voiceStarted = false;
        voicePreparing = false;
        final AudioRecord rec = recorder;
        recorder = null;
        final Thread rt = recordThread;
        final int len = voiceLen;
        final short[] pcm = new short[Math.max(0, len)];
        if (len > 0) System.arraycopy(voiceData, 0, pcm, 0, len);
        final String previewText = voicePreviewText;
        updatePinyinRow();
        new Thread(() -> {
            try {
                if (rec != null) rec.stop();
            } catch (Throwable ignored) {
            }
            if (rt != null) {
                try {
                    rt.join(800);
                } catch (InterruptedException ignored) {
                }
            }
            recordThread = null;
            try {
                if (rec != null) rec.release();
            } catch (Throwable ignored) {
            }
            if (len < 1600) {
                postUi(() -> {
                    voicePreviewText = "";
                    toast("录音太短，请按住多说一会");
                    updatePinyinRow();
                });
                return;
            }
            String r = voice.recognize(pcm, 16000);
            if (r == null || r.isEmpty()) r = previewText;
            final String result = r;
            postUi(() -> {
                voicePreviewText = "";
                if (result != null && !result.isEmpty()) {
                    commitTextSafe(result);
                    log("语音上屏");
                } else {
                    toast("没听清，请再说一次");
                }
                updatePinyinRow();
            });
        }, "voice-finish").start();
    }

    private View.OnTouchListener backspaceTouch() {
        return (v, e) -> {
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                lastKeyTime = System.currentTimeMillis();
                v.setAlpha(0.7f);
                log("退格");
                onBackspace();
                clearPendingBackspace();
                pendingBackspaceView = v;
                pendingBackspace = new Runnable() {
                    @Override
                    public void run() {
                        if (v.getAlpha() >= 1f) return;
                        onBackspace();
                        v.postDelayed(this, 110);
                    }
                };
                v.postDelayed(pendingBackspace, 400);
                return true;
            }
            if (e.getAction() == MotionEvent.ACTION_UP || e.getAction() == MotionEvent.ACTION_CANCEL) {
                v.setAlpha(1f);
                v.setPressed(false);
                clearPendingBackspace();
                return true;
            }
            return false;
        };
    }

    private void clearPendingBackspace() {
        if (pendingBackspace != null && pendingBackspaceView != null) {
            pendingBackspaceView.removeCallbacks(pendingBackspace);
        }
        pendingBackspace = null;
        pendingBackspaceView = null;
    }

    private void onBackspace() {
        seqCandidates.clear();
        layoutMenu = false;
        if (aiMode) {
            if (composing.length() > 0) {
                composing.setLength(composing.length() - 1);
                assocMode = false;
                candidates.clear();
                refreshCandidates();
                updatePinyinRow();
                return;
            }
            try {
                int st = aiEdit.getSelectionStart();
                int en = aiEdit.getSelectionEnd();
                int len = aiEdit.length();
                if (st < 0 || st > len) st = len;
                if (en < 0 || en > len) en = st;
                if (en > st) {
                    aiEdit.getText().delete(st, en);
                    aiEdit.setSelection(st);
                } else if (st > 0) {
                    aiEdit.getText().delete(st - 1, st);
                    aiEdit.setSelection(st - 1);
                }
            } catch (Throwable ignored) {
            }
            return;
        }
        if (composing.length() > 0) {
            char removed = composing.charAt(composing.length() - 1);
            composing.setLength(composing.length() - 1);
            lastDeletedLetter = removed;
            lastBackspaceTime = System.currentTimeMillis();
            assocMode = false;
            refreshCandidates();
            updatePinyinRow();
            return;
        }
        if (assocMode) {
            assocMode = false;
            candidates.clear();
            renderCandidates();
            updatePinyinRow();
            return;
        }
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        try {
            CharSequence sel = ic.getSelectedText(0);
            if (sel != null && sel.length() > 0) {
                ic.commitText("", 1);
                log("全选删除");
                return;
            }
            ExtractedText et = ic.getExtractedText(new ExtractedTextRequest(), 0);
            if (et != null && et.selectionStart >= 0 && et.selectionEnd > et.selectionStart) {
                ic.commitText("", 1);
                log("全选删除(ExtractedText)");
                return;
            }
        } catch (Throwable ignored) {
        }
        ic.deleteSurroundingText(1, 0);
    }

    private void onEnter() {
        seqCandidates.clear();
        layoutMenu = false;
        if (aiMode) {
            clearComposingRow();
            aiInsert("\n");
            return;
        }
        if (composing.length() > 0 && !candidates.isEmpty()) commitCandidate(0);
        log("回车");
        try {
            if (imeAction == EditorInfo.IME_ACTION_SEND || imeAction == EditorInfo.IME_ACTION_SEARCH
                    || imeAction == EditorInfo.IME_ACTION_GO || imeAction == EditorInfo.IME_ACTION_DONE
                    || imeAction == EditorInfo.IME_ACTION_NEXT || imeAction == EditorInfo.IME_ACTION_PREVIOUS) {
                sendDefaultEditorAction(true);
            } else {
                commitTextSafe("\n");
            }
        } catch (Throwable t) {
            commitTextSafe("\n");
        }
    }

    private String enterLabel() {
        if (imeAction == EditorInfo.IME_ACTION_SEND) return "⏎发送";
        if (imeAction == EditorInfo.IME_ACTION_SEARCH) return "⏎搜索";
        if (imeAction == EditorInfo.IME_ACTION_GO) return "⏎前往";
        if (imeAction == EditorInfo.IME_ACTION_DONE) return "⏎完成";
        if (imeAction == EditorInfo.IME_ACTION_NEXT) return "⏎下一项";
        if (imeAction == EditorInfo.IME_ACTION_PREVIOUS) return "⏎上一项";
        return "⏎回车";
    }

    private String shiftLabel() {
        if (shiftState == 2) return "⇧锁";
        if (shiftState == 1) return "⇧";
        return "⇧";
    }

    private void cycleShift() {
        long now = System.currentTimeMillis();
        boolean dbl = now - lastShiftTime < 350;
        if (dbl) {
            shiftState = shiftState == 2 ? 0 : 2;
        } else {
            shiftState = shiftState == 1 ? 0 : 1;
        }
        lastShiftTime = now;
        lastKeyTime = now;
        seqCandidates.clear();
        layoutMenu = false;
        rebuildKeyboard();
        toast(shiftState == 2 ? "大写锁定" : shiftState == 1 ? "临时大写" : "小写");
    }

    private void inputLetter(char c) {
        seqCandidates.clear();
        layoutMenu = false;
        if (aiMode && !chinese) {
            char out = shiftState != 0 ? Character.toUpperCase(c) : c;
            if (shiftState == 1) shiftState = 0;
            log("改写英文 " + c);
            aiInsert(String.valueOf(out));
            return;
        }
        if (password) {
            log("密码键 " + c);
            commitRaw(String.valueOf(glyph(c)));
            if (shiftState == 1) shiftState = 0;
            return;
        }
        if (!chinese) {
            char out = shiftState != 0 ? Character.toUpperCase(c) : c;
            log("英文 " + c);
            commitTextSafe(String.valueOf(out));
            if (shiftState == 1) shiftState = 0;
            return;
        }
        composing.append(c);
        log("键 " + c);
        if (System.currentTimeMillis() - lastBackspaceTime < 3000 && lastDeletedLetter != 0
                && TouchModel.areNeighbors(lastDeletedLetter, c)) {
            touch.noteCorrection(lastDeletedLetter, c);
            touch.discount(lastDeletedLetter);
        }
        assocMode = false;
        refreshCandidates();
        updatePinyinRow();
        if (shiftState == 1) shiftState = 0;
    }

    private char glyph(char c) {
        return shiftState != 0 ? Character.toUpperCase(c) : c;
    }

    private void refreshCandidates() {
        if (password) {
            candidates = new ArrayList<>();
            seqCandidates.clear();
            layoutMenu = false;
            renderCandidates();
            return;
        }
        String q = composing.toString();
        if (q.isEmpty()) {
            candidates = new ArrayList<>();
            renderCandidates();
            return;
        }
        if (!chinese) {
            candidates = englishCandidates(q);
            renderCandidates();
            return;
        }
        if (!dictLoaded || engine == null) {
            candidates = new ArrayList<>();
            renderCandidates();
            return;
        }
        if (q.length() >= 2 && !engine.hasPinyinHit(q)) {
            candidates = englishCandidates(q);
            assocMode = false;
            renderCandidates();
            return;
        }
        candidates = engine.candidates(q);
        assocMode = false;
        renderCandidates();
    }

    private List<String> englishCandidates(String q) {
        ArrayList<String> out = new ArrayList<>();
        if (q.length() < 2) return out;
        String lower = q.toLowerCase();
        if (memory.englishCountOf(lower) > 0) out.add(lower);
        ArrayList<String> known = new ArrayList<>();
        for (String w : memory.englishTop(60)) {
            if (w.startsWith(lower) && !known.contains(w) && !out.contains(w)) known.add(w);
            if (known.size() >= 8) break;
        }
        known.sort((a, b) -> {
            int ca = memory.englishCountOf(a);
            int cb = memory.englishCountOf(b);
            if (ca != cb) return Integer.compare(cb, ca);
            return Integer.compare(a.length(), b.length());
        });
        out.addAll(known);
        if (!out.contains(lower)) out.add(lower);
        String cap = Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
        if (!out.contains(cap)) out.add(cap);
        if (out.size() > 10) out = new ArrayList<>(out.subList(0, 10));
        return out;
    }

    private void renderCandidates() {
        if (candRow == null) return;
        candRow.removeAllViews();
        if (layoutMenu && composing.length() == 0 && !assocMode && !password) {
            String[] names = {"全键盘", "九键", "手写"};
            for (int i = 0; i < names.length; i++) {
                final int idx = i;
                TextView tv = new TextView(this);
                tv.setText(names[i]);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
                tv.setTextColor(i == layoutMode ? MiraTheme.ACCENT : MiraTheme.CARD_INK);
                tv.setGravity(Gravity.CENTER);
                tv.setSingleLine(true);
                tv.setPadding(dp(14), 0, dp(14), 0);
                tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
                tv.setOnClickListener(v -> selectLayout(idx));
                candRow.addView(tv);
            }
            candRow.setVisibility(View.VISIBLE);
            candScroll.post(() -> candScroll.scrollTo(0, 0));
            return;
        }
        if (!seqCandidates.isEmpty() && composing.length() == 0 && !assocMode && !password) {
            for (int i = 0; i < seqCandidates.size(); i++) {
                final int idx = i;
                TextView tv = new TextView(this);
                tv.setText(seqCandidates.get(i));
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                tv.setTextColor(i == 0 ? MiraTheme.ACCENT : MiraTheme.CARD_INK);
                tv.setGravity(Gravity.CENTER);
                tv.setSingleLine(true);
                tv.setPadding(dp(14), 0, dp(14), 0);
                tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
                tv.setOnClickListener(v -> commitSeq(idx));
                candRow.addView(tv);
            }
            candRow.setVisibility(View.VISIBLE);
            candScroll.post(() -> candScroll.scrollTo(0, 0));
            return;
        }
        if (assocMode) {
            TextView tag = new TextView(this);
            tag.setText("联想");
            tag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            tag.setTextColor(MiraTheme.CARD_SUB);
            tag.setPadding(dp(6), 0, dp(8), 0);
            tag.setGravity(Gravity.CENTER);
            candRow.addView(tag);
        }
        if (candidates.isEmpty()) {
            if (!assocMode && !password && composing.length() == 0 && clipPreview != null
                    && System.currentTimeMillis() - clipAt < 90000) {
                TextView clip = new TextView(this);
                clip.setText("粘贴：" + clipPreview);
                clip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                clip.setTextColor(MiraTheme.ACCENT);
                clip.setGravity(Gravity.CENTER);
                clip.setSingleLine(true);
                clip.setPadding(dp(14), 0, dp(14), 0);
                clip.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
                clip.setOnClickListener(v -> commitTextSafe(clipFull));
                candRow.addView(clip);
                candRow.setVisibility(View.VISIBLE);
                return;
            }
            if (assocMode) {
                TextView empty = new TextView(this);
                empty.setText("没有更多联想");
                empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                empty.setTextColor(MiraTheme.CARD_SUB);
                empty.setPadding(dp(8), 0, dp(8), 0);
                empty.setGravity(Gravity.CENTER);
                candRow.addView(empty);
                candRow.setVisibility(View.VISIBLE);
                return;
            }
            candRow.setVisibility(View.GONE);
            return;
        }
        candRow.setVisibility(View.VISIBLE);
        for (int i = 0; i < candidates.size(); i++) {
            final int idx = i;
            TextView tv = new TextView(this);
            tv.setText(candidates.get(i));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            boolean accent = !assocMode && i == 0;
            tv.setTextColor(accent ? MiraTheme.ACCENT : MiraTheme.CARD_INK);
            tv.setGravity(Gravity.CENTER);
            tv.setSingleLine(true);
            tv.setPadding(dp(14), 0, dp(14), 0);
            tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
            tv.setOnClickListener(v -> commitCandidate(idx));
            candRow.addView(tv);
        }
        candScroll.post(() -> candScroll.scrollTo(0, 0));
    }

    private void commitCandidate(int i) {
        if (i < 0 || i >= candidates.size()) return;
        String w = candidates.get(i);
        boolean wasAssoc = assocMode;
        seqCandidates.clear();
        layoutMenu = false;
        log("候选 " + preview(w, 6));
        commitTextSafe(w);
        composing.setLength(0);
        assocMode = false;
        candidates.clear();
        if (chinese && !password && dictLoaded && engine != null) {
            showAssoc(w);
        } else {
            renderCandidates();
            updatePinyinRow();
        }
        if (wasAssoc) log("联想上屏 " + preview(w, 6));
    }

    private void showAssoc(String word) {
        if (aiMode || password || !chinese || !dictLoaded || engine == null || word == null || word.isEmpty()) {
            renderCandidates();
            updatePinyinRow();
            return;
        }
        String last = word.substring(word.length() - 1);
        List<String> a = engine.associate(last);
        if (a.isEmpty()) {
            assocMode = false;
            candidates.clear();
        } else {
            assocMode = true;
            candidates = a;
        }
        renderCandidates();
        updatePinyinRow();
    }

    private void clearComposingRow() {
        if (composing.length() == 0 && !assocMode) return;
        composing.setLength(0);
        assocMode = false;
        candidates.clear();
        renderCandidates();
        updatePinyinRow();
    }

    private void commitTextSafe(String text) {
        if (text == null) return;
        if (aiMode) {
            aiInsert(text);
            clearComposingRow();
            return;
        }
        try {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(text, 1);
        } catch (Throwable ignored) {
        }
        if (!password && !text.isEmpty()) {
            if (DictRepo.hasHan(text)) {
                if (memory.learn(text)) {
                    if (memory.needFlush()) flushMemory();
                }
            } else if (isAsciiWord(text)) {
                memory.learnEnglish(text);
                if (memory.needFlush()) flushMemory();
            }
        }
        if (composing.length() > 0) {
            composing.setLength(0);
            assocMode = false;
            refreshCandidates();
            updatePinyinRow();
        }
        maybeSeq(text);
    }

    private void maybeSeq(String text) {
        if (text == null || text.length() != 1) return;
        char c = text.charAt(0);
        if (c >= '0' && c <= '9') {
            seqCandidates.clear();
            layoutMenu = false;
            return;
        }
        if (!isSeqPunct(c)) return;
        if (aiMode || password || composing.length() > 0 || assocMode) return;
        try {
            InputConnection ic = getCurrentInputConnection();
            CharSequence before = ic == null ? null : ic.getTextBeforeCursor(400, 0);
            if (before == null) return;
            SeqToken tok = findLastSeq(before.toString());
            if (tok == null || tok.end >= tok.lineLen) {
                seqCandidates.clear();
                return;
            }
            seqCandidates.clear();
            if (isEndPunct(c)) {
                seqCandidates.add(nextSeqLabel(tok));
            } else if (chinese) {
                seqCandidates.add("。");
                seqCandidates.add("！");
                seqCandidates.add("？");
            } else {
                seqCandidates.add(".");
                seqCandidates.add("!");
                seqCandidates.add("?");
            }
            layoutMenu = false;
            renderCandidates();
        } catch (Throwable ignored) {
        }
    }

    private void commitSeq(int i) {
        if (i < 0 || i >= seqCandidates.size()) return;
        String s = seqCandidates.get(i);
        seqCandidates.clear();
        commitTextSafe(s);
        renderCandidates();
        updatePinyinRow();
    }

    private static boolean isSeqPunct(char c) {
        return c == ',' || c == ';' || c == '，' || c == '、' || c == '；'
                || c == '。' || c == '！' || c == '？'
                || c == '.' || c == '!' || c == '?';
    }

    private static boolean isEndPunct(char c) {
        return c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?';
    }

    private SeqToken findLastSeq(String text) {
        int end = text.length();
        int lines = 0;
        while (end > 0 && lines < 6) {
            int nl = text.lastIndexOf('\n', end - 1);
            int start = nl + 1;
            String line = text.substring(start, end);
            String trim = line.trim();
            SeqToken t = parseSeqPrefix(trim);
            if (t != null) {
                t.lineLen = trim.length();
                return t;
            }
            end = start - 1;
            lines++;
        }
        return null;
    }

    private SeqToken parseSeqPrefix(String s) {
        if (s == null || s.isEmpty()) return null;
        int i = 0;
        boolean paren = false;
        if (s.charAt(0) == '（' || s.charAt(0) == '(') {
            paren = true;
            i = 1;
        }
        int j = i;
        while (j < s.length() && s.charAt(j) >= '0' && s.charAt(j) <= '9') j++;
        if (j > i) {
            int number;
            try {
                number = Integer.parseInt(s.substring(i, j));
            } catch (Throwable t) {
                return null;
            }
            if (paren) {
                if (j < s.length() && (s.charAt(j) == '）' || s.charAt(j) == ')')) {
                    return new SeqToken(number, 1, s.substring(0, 1), String.valueOf(s.charAt(j)), j + 1);
                }
                return null;
            }
            if (j < s.length()) {
                char sep = s.charAt(j);
                if (sep == '.' || sep == '、' || sep == ')' || sep == '）') {
                    return new SeqToken(number, 0, "", String.valueOf(sep), j + 1);
                }
            }
            return null;
        }
        int k = 0;
        while (k < s.length() && isCnDigit(s.charAt(k))) k++;
        if (k > 0 && k < s.length()) {
            char sep = s.charAt(k);
            if (sep == '、' || sep == '.') {
                int number = cnToInt(s.substring(0, k));
                if (number > 0) return new SeqToken(number, 2, "", String.valueOf(sep), k + 1);
            }
        }
        return null;
    }

    private String nextSeqLabel(SeqToken t) {
        int n = t.number + 1;
        if (t.style == 2) return intToCn(n) + t.suffix;
        return (t.style == 1 ? t.prefix : "") + n + t.suffix;
    }

    private static boolean isCnDigit(char c) {
        return c == '一' || c == '二' || c == '三' || c == '四' || c == '五'
                || c == '六' || c == '七' || c == '八' || c == '九' || c == '十';
    }

    private static int cnDigit(char c) {
        switch (c) {
            case '一': return 1;
            case '二': return 2;
            case '三': return 3;
            case '四': return 4;
            case '五': return 5;
            case '六': return 6;
            case '七': return 7;
            case '八': return 8;
            case '九': return 9;
            default: return 0;
        }
    }

    private static int cnToInt(String s) {
        int ten = s.indexOf('十');
        if (ten < 0) {
            int v = 0;
            for (int i = 0; i < s.length(); i++) {
                int d = cnDigit(s.charAt(i));
                if (d == 0) return 0;
                v = v * 10 + d;
            }
            return v;
        }
        int left = ten == 0 ? 1 : cnDigit(s.charAt(0));
        int right = ten == s.length() - 1 ? 0 : cnDigit(s.charAt(ten + 1));
        return left * 10 + right;
    }

    private static String intToCn(int n) {
        if (n <= 0) return "零";
        if (n < 10) return String.valueOf(CN_DIGITS.charAt(n));
        if (n < 20) {
            int r = n % 10;
            return "十" + (r == 0 ? "" : String.valueOf(CN_DIGITS.charAt(r)));
        }
        if (n < 100) {
            int r = n % 10;
            return String.valueOf(CN_DIGITS.charAt(n / 10)) + "十"
                    + (r == 0 ? "" : String.valueOf(CN_DIGITS.charAt(r)));
        }
        return String.valueOf(n);
    }

    private static final class SeqToken {
        final int number;
        final int style;
        final String prefix;
        final String suffix;
        final int end;
        int lineLen;

        SeqToken(int number, int style, String prefix, String suffix, int end) {
            this.number = number;
            this.style = style;
            this.prefix = prefix;
            this.suffix = suffix;
            this.end = end;
        }
    }

    private static final String CN_DIGITS = "零一二三四五六七八九";

    private void commitRaw(String text) {
        try {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) ic.commitText(text, 1);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isAsciiWord(String s) {
        if (s.length() < 2) return false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))) return false;
        }
        return true;
    }

    private void flushMemory() {
        try {
            prefs.putString(Prefs.MEMORY, memory.serialize());
            prefs.putString(Prefs.MEMORY_EN, memory.serializeEnglish());
            prefs.putString(Prefs.TOUCH_JSON, touch.exportJson());
            memory.flushDone();
        } catch (Throwable ignored) {
        }
    }

    private void updatePinyinRow() {
        if (pinyinView == null || toolRow == null || pinyinRow == null) return;
        boolean has = composing.length() > 0 || assocMode || password || !chinese
                || recording || voiceStarted || voicePreparing || voiceNoPerm || !dictLoaded;
        pinyinRow.setVisibility(has ? View.VISIBLE : View.GONE);
        toolRow.setVisibility(has ? View.GONE : View.VISIBLE);
        clearView.setVisibility((composing.length() > 0 || assocMode) ? View.VISIBLE : View.GONE);
        if (recording || voiceStarted) {
            String t = "🎤 正在录音… 松开识别";
            if (voicePreviewText != null && !voicePreviewText.isEmpty()) t += "  " + voicePreviewText;
            pinyinView.setText(t);
            pinyinView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            pinyinView.setTextColor(MiraTheme.ACCENT);
            return;
        }
        if (voicePreparing) {
            pinyinView.setText("🎤 正在准备语音引擎…");
            pinyinView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            pinyinView.setTextColor(MiraTheme.CARD_SUB);
            return;
        }
        if (voiceNoPerm) {
            pinyinView.setText("🎤 无麦克风权限 · 去设置页授权");
            pinyinView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            pinyinView.setTextColor(MiraTheme.CARD_SUB);
            return;
        }
        if (password) {
            pinyinView.setText("🔒 密码输入 · 候选停用");
            pinyinView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            pinyinView.setTextColor(MiraTheme.CARD_SUB);
            return;
        }
        if (!chinese) {
            pinyinView.setText("英文 · abc");
            pinyinView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            pinyinView.setTextColor(MiraTheme.CARD_SUB);
            return;
        }
        if (!dictLoaded) {
            pinyinView.setText("词库准备中… " + composing);
            pinyinView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            pinyinView.setTextColor(MiraTheme.CARD_SUB);
            return;
        }
        if (composing.length() > 0) {
            pinyinView.setText(composing.toString());
            pinyinView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            pinyinView.setTextColor(MiraTheme.ACCENT);
            return;
        }
        if (assocMode) {
            pinyinView.setText("联想");
            pinyinView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            pinyinView.setTextColor(MiraTheme.CARD_SUB);
            return;
        }
        String s = "拼音 · lt=聊天";
        if (engine != null && engine.isFuzzyEnabled()) s += " · 模糊已开";
        pinyinView.setText(s);
        pinyinView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        pinyinView.setTextColor(MiraTheme.CARD_SUB);
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        try {
            imeAction = info == null ? EditorInfo.IME_ACTION_NONE : info.imeOptions & EditorInfo.IME_MASK_ACTION;
            password = isPassword(info);
            composing.setLength(0);
            assocMode = false;
            candidates.clear();
            seqCandidates.clear();
            layoutMenu = false;
            voicePreviewText = "";
            voicePreparing = false;
            voiceNoPerm = false;
            aiMode = false;
            if (aiPanel != null) aiPanel.setVisibility(View.GONE);
            int theme = prefs.getInt(Prefs.THEME, MiraTheme.MIRA);
            boolean fc = prefs.getBool(Prefs.FREQ_COLOR, true);
            boolean needRebuild = theme != MiraTheme.index || fc != MiraTheme.freqColor;
            if (needRebuild) {
                MiraTheme.applyTheme(theme);
                MiraTheme.freqColor = fc;
            }
            if (engine != null) engine.setFuzzyEnabled(prefs.getBool(Prefs.FUZZY, true));
            touch.setEnabled(prefs.getBool(Prefs.TOUCH_ON, true));
            readClip();
            if (root != null) {
                root.setBackgroundColor(MiraTheme.KB_BG);
                topBar.setBackgroundColor(MiraTheme.KB_BG);
                candScroll.setBackgroundColor(MiraTheme.KB_BG);
            }
            if (panelMode != 0) {
                panelMode = 0;
                needRebuild = true;
            }
            if (imeAction != lastBuiltAction) {
                lastBuiltAction = imeAction;
                needRebuild = true;
            }
            if (needRebuild) rebuildKeyboardSoon();
            refreshCandidates();
            updatePinyinRow();
        } catch (Throwable t) {
            log("onStartInputView：" + t.getClass().getSimpleName());
        }
    }

    private void loadUserWords() {
        try {
            for (String w : Prefs.splitPipe(prefs.getString(Prefs.USER_WORDS, ""), 500)) {
                if (!w.isEmpty()) repo.addUserWord(w);
            }
        } catch (Throwable ignored) {
        }
    }

    private void loadImportedFiles() {
        try {
            java.io.File dir = new java.io.File(getFilesDir(), "dicts");
            java.io.File[] fs = dir.listFiles();
            if (fs == null) return;
            for (java.io.File f : fs) {
                String key = f.getName() + ":" + f.lastModified();
                if (loadedDicts.contains(key)) continue;
                String text = readTextFile(f);
                if (text != null && !text.isEmpty()) repo.importText(text);
                loadedDicts.add(key);
            }
        } catch (Throwable ignored) {
        }
    }

    private static String readTextFile(java.io.File f) {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new java.io.FileInputStream(f), java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            br.close();
            return sb.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    private void readClip() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) {
                clipPreview = null;
                return;
            }
            android.content.ClipDescription desc = cm.getPrimaryClipDescription();
            long ts = desc == null ? 0 : desc.getTimestamp();
            if (System.currentTimeMillis() - ts > 90000) {
                clipPreview = null;
                return;
            }
            ClipData cd = cm.getPrimaryClip();
            if (cd == null || cd.getItemCount() == 0) {
                clipPreview = null;
                return;
            }
            CharSequence cs = cd.getItemAt(0).coerceToText(this);
            if (cs == null || cs.toString().trim().isEmpty()) {
                clipPreview = null;
                return;
            }
            clipFull = cs.toString();
            clipAt = System.currentTimeMillis();
            clipPreview = preview(clipFull, 16);
        } catch (Throwable t) {
            clipPreview = null;
        }
    }

    private void registerClipListener() {
        try {
            clipManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipManager == null) return;
            clipListener = this::onClipChanged;
            clipManager.addPrimaryClipChangedListener(clipListener);
        } catch (Throwable ignored) {
        }
    }

    private void unregisterClipListener() {
        try {
            if (clipManager != null && clipListener != null) {
                clipManager.removePrimaryClipChangedListener(clipListener);
            }
        } catch (Throwable ignored) {
        }
        clipManager = null;
        clipListener = null;
    }

    private void onClipChanged() {
        try {
            if (clipManager == null || !clipManager.hasPrimaryClip()) return;
            ClipData cd = clipManager.getPrimaryClip();
            if (cd == null || cd.getItemCount() == 0) return;
            CharSequence cs = cd.getItemAt(0).coerceToText(this);
            if (cs == null || cs.toString().trim().isEmpty()) return;
            prefs.addClip(cs.toString());
            clipFull = cs.toString();
            clipAt = System.currentTimeMillis();
            clipPreview = preview(clipFull, 16);
            if (root != null) postUi(this::refreshCandidates);
        } catch (Throwable ignored) {
        }
    }

    private boolean isPassword(EditorInfo info) {
        if (info == null) return false;
        int cls = info.inputType & InputType.TYPE_MASK_CLASS;
        if (cls != InputType.TYPE_CLASS_TEXT) return false;
        int v = info.inputType & InputType.TYPE_MASK_VARIATION;
        return v == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    }

    private void scheduleRebuild() {
        if (root == null) return;
        long dt = System.currentTimeMillis() - lastKeyTime;
        long delay = dt < 400 ? 400 - dt : 0;
        root.postDelayed(() -> {
            try {
                rebuildKeyboard();
                updatePinyinRow();
                refreshCandidates();
            } catch (Throwable ignored) {
            }
        }, delay);
    }

    @Override
    public boolean onEvaluateInputViewShown() {
        return true;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        flushMemory();
        candidates.clear();
        composing.setLength(0);
        assocMode = false;
        seqCandidates.clear();
        layoutMenu = false;
        voicePreviewText = "";
        voicePreparing = false;
        voiceNoPerm = false;
        if (recording || voiceStarted || voiceStopPending) stopVoice();
    }

    @Override
    public void onDestroy() {
        current = null;
        unregisterClipListener();
        clearPendingLong();
        clearPendingBackspace();
        if (pendingRebuild != null && root != null) {
            root.removeCallbacks(pendingRebuild);
            pendingRebuild = null;
        }
        flushMemory();
        recording = false;
        try {
            voice.release();
        } catch (Throwable ignored) {
        }
        if (recorder != null) {
            try {
                recorder.release();
            } catch (Throwable ignored) {
            }
            recorder = null;
        }
        super.onDestroy();
    }

    private GradientDrawable bg(int color, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        return g;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable t) {
            return "1.0";
        }
    }

    private void postUi(Runnable r) {
        if (r == null) return;
        if (root != null) root.post(r);
        else new Handler(Looper.getMainLooper()).post(r);
    }

    private void postUiDelayed(Runnable r, long ms) {
        if (r == null) return;
        if (root != null) root.postDelayed(r, ms);
        else new Handler(Looper.getMainLooper()).postDelayed(r, ms);
    }

    private void toast(String s) {
        try {
            Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }

    private void log(String s) {
        if (s == null) return;
        synchronized (logs) {
            logs.addFirst(s);
            while (logs.size() > 14) logs.removeLast();
        }
    }

    private void showLogs() {
        StringBuilder sb = new StringBuilder();
        synchronized (logs) {
            for (String s : logs) sb.append("· ").append(s).append('\n');
        }
        if (sb.length() == 0) sb.append("· 暂无记录\n");
        try {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("按键自检 · 最近 14 条");
            TextView tv = new TextView(this);
            tv.setText(sb.toString());
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            tv.setTextColor(MiraTheme.PAGE_INK);
            tv.setPadding(dp(16), dp(10), dp(16), dp(10));
            ScrollView sv = new ScrollView(this);
            sv.addView(tv);
            b.setView(sv);
            b.setPositiveButton("关闭", null);
            b.show();
        } catch (Throwable t) {
            toast(sb.toString());
        }
    }

    private class HandView extends View {
        private final Paint paint = new Paint();
        private final Path path = new Path();

        HandView(Context context) {
            super(context);
            paint.setAntiAlias(true);
            paint.setDither(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(9f);
            paint.setColor(MiraTheme.PEN);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawPath(path, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) path.moveTo(event.getX(), event.getY());
            else if (event.getAction() == MotionEvent.ACTION_MOVE) path.lineTo(event.getX(), event.getY());
            invalidate();
            return true;
        }

        void clear() {
            path.reset();
            invalidate();
        }
    }
}