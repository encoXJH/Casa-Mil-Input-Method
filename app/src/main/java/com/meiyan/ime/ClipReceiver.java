package com.meiyan.ime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.meiyan.ime.store.Prefs;

public class ClipReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent == null) return;
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (text == null) return;
            new Prefs(context).addClip(text.toString());
            Toast.makeText(context, "已收进剪贴板历史", Toast.LENGTH_SHORT).show();
        } catch (Throwable ignored) {
        }
    }
}