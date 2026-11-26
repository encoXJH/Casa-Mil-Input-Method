package com.meiyan.ime;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

public class VoiceRelayActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            Intent in = getIntent();
            String text = in == null ? null : in.getStringExtra("text");
            if (text != null && !text.isEmpty()) {
                Intent out = new Intent();
                out.putExtra("text", text);
                setResult(RESULT_OK, out);
                Toast.makeText(this, "语音结果已回传", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable ignored) {
        }
        finish();
    }
}