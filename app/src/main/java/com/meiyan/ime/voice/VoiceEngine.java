package com.meiyan.ime.voice;

import android.content.Context;

import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;

public class VoiceEngine {
    public static final String MODEL = "model.int8.onnx";
    public static final String TOKENS = "tokens.txt";

    private volatile OfflineRecognizer recognizer;
    private volatile boolean ready;
    private volatile boolean failed;
    private volatile boolean initializing;
    private volatile String status = "未初始化";

    public boolean isReady() {
        return ready;
    }

    public boolean isInitializing() {
        return initializing;
    }

    public boolean isFailed() {
        return failed;
    }

    public String status() {
        return status;
    }

    public static boolean modelInAssets(Context ctx) {
        try {
            String[] list = ctx.getAssets().list("");
            if (list == null) return false;
            boolean m = false, t = false;
            for (String s : list) {
                if (MODEL.equals(s)) m = true;
                if (TOKENS.equals(s)) t = true;
            }
            return m && t;
        } catch (Exception e) {
            return false;
        }
    }

    public synchronized void init(Context ctx) {
        if (ready || initializing) return;
        initializing = true;
        status = "正在准备语音引擎…";
        try {
            if (!modelInAssets(ctx)) {
                status = "语音模型未内置";
                failed = true;
                return;
            }
            OfflineParaformerModelConfig pm = new OfflineParaformerModelConfig();
            pm.setModel(MODEL);

            OfflineModelConfig mc = new OfflineModelConfig();
            mc.setParaformer(pm);
            mc.setTokens(TOKENS);
            mc.setNumThreads(2);
            mc.setDebug(false);
            mc.setProvider("cpu");
            mc.setModelType("paraformer");

            FeatureConfig fc = new FeatureConfig();
            fc.setSampleRate(16000);
            fc.setFeatureDim(80);

            OfflineRecognizerConfig cfg = new OfflineRecognizerConfig();
            cfg.setFeatConfig(fc);
            cfg.setModelConfig(mc);
            cfg.setDecodingMethod("greedy_search");

            recognizer = new OfflineRecognizer(ctx.getAssets(), cfg);
            ready = true;
            status = "语音引擎已就绪";
        } catch (Throwable t) {
            failed = true;
            status = "语音引擎初始化失败：" + t.getClass().getSimpleName();
        } finally {
            initializing = false;
        }
    }

    public synchronized String recognize(short[] pcm, int sampleRate) {
        OfflineRecognizer r = recognizer;
        if (!ready || r == null || pcm == null || pcm.length < 1600) return null;
        try {
            float[] samples = new float[pcm.length];
            for (int i = 0; i < pcm.length; i++) samples[i] = pcm[i] / 32768.0f;
            OfflineStream stream = r.createStream();
            stream.acceptWaveform(samples, sampleRate);
            r.decode(stream);
            OfflineRecognizerResult result = r.getResult(stream);
            stream.release();
            if (result == null) return null;
            String text = result.getText();
            return text == null ? null : text.trim();
        } catch (Throwable t) {
            return null;
        }
    }

    public synchronized void release() {
        try {
            if (recognizer != null) recognizer.release();
        } catch (Throwable ignored) {
        }
        recognizer = null;
        ready = false;
    }

    public String statusForRow() {
        if (ready) return "🎤 语音引擎已就绪";
        if (failed) return "🎤 " + status;
        return "🎤 正在准备语音引擎…";
    }
}