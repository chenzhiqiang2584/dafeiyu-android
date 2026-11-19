package com.dafeiyu.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 第一次打开：填 DeepSeek API Key。
 *
 * 重做原因：原来背景写死浅灰 0xFFF5F6F8，文字却跟 Theme.text() 走，
 * 深色主题下浅色字压在浅色背景上，看着难受。
 * 现在整套配色都跟主题走，布局也收紧了。
 */
public class SetupActivity extends Activity {

    private EditText keyInput;
    private EditText nameInput;
    private TextView status;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        Theme.dark = prefs().getBoolean(MainActivity.K_THEME, false);
        buildUI();
        try {
            FontKit.apply(this);
        } catch (Throwable ignored) {}
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(MainActivity.PREF, Context.MODE_PRIVATE);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void buildUI() {
        ScrollView sc = new ScrollView(this);
        sc.setBackgroundColor(Theme.bgTop());
        sc.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(26), dp(64), dp(26), dp(32));

        // 标题
        TextView t1 = new TextView(this);
        t1.setText("大肥鱼");
        t1.setTextSize(32);
        t1.setTextColor(Theme.text());
        root.addView(t1);

        TextView t2 = new TextView(this);
        t2.setText("一条住在手机里的蓝鲸");
        t2.setTextSize(14);
        t2.setTextColor(Theme.muted());
        t2.setPadding(0, dp(6), 0, dp(36));
        root.addView(t2);

        // ---- API Key ----
        root.addView(label("DeepSeek API Key"));
        keyInput = new EditText(this);
        keyInput.setHint("sk-...");
        keyInput.setHintTextColor(Theme.muted());
        keyInput.setTextSize(15);
        keyInput.setSingleLine(true);
        keyInput.setTextColor(Theme.text());
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setText(prefs().getString(MainActivity.K_APIKEY, ""));
        keyInput.setBackground(inputBox());
        keyInput.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.addView(keyInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText("在 platform.deepseek.com 建一个。只存在本机，不上传。");
        hint.setTextSize(12);
        hint.setTextColor(Theme.muted());
        hint.setLineSpacing(0f, 1.3f);
        hint.setPadding(dp(2), dp(8), 0, dp(28));
        root.addView(hint);

        // ---- 名字 ----
        root.addView(label("她叫什么"));
        nameInput = new EditText(this);
        nameInput.setHint("大肥鱼");
        nameInput.setHintTextColor(Theme.muted());
        nameInput.setTextSize(15);
        nameInput.setSingleLine(true);
        nameInput.setTextColor(Theme.text());
        String savedName = prefs().getString(MainActivity.K_NAME, "");
        nameInput.setText(savedName.isEmpty() ? "大肥鱼" : savedName);
        nameInput.setBackground(inputBox());
        nameInput.setPadding(dp(16), dp(14), dp(16), dp(14));
        root.addView(nameInput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ---- 测试按钮（次要）----
        TextView test = new TextView(this);
        test.setText("先测测 Key 通不通");
        test.setTextSize(14);
        test.setTextColor(Theme.accent());
        test.setGravity(Gravity.CENTER);
        test.setPadding(0, dp(28), 0, dp(14));
        test.setOnClickListener(v -> testKey());
        root.addView(test);

        // ---- 主按钮 ----
        TextView save = new TextView(this);
        save.setText("开始");
        save.setTextSize(17);
        save.setTextColor(0xFFFFFFFF);
        save.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(Theme.dark ? Theme.IM_MINE_D : Theme.IM_MINE);
        save.setBackground(g);
        save.setOnClickListener(v -> save());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(54));
        root.addView(save, slp);

        // 状态
        status = new TextView(this);
        status.setTextSize(13);
        status.setLineSpacing(0f, 1.35f);
        status.setPadding(dp(4), dp(18), dp(4), 0);
        status.setTextColor(Theme.muted());
        root.addView(status);

        // ---- 底部：记忆统计（收起来，不抢眼）----
        TextView mem = new TextView(this);
        mem.setTextSize(11.5f);
        mem.setTextColor(Theme.muted());
        mem.setPadding(0, dp(48), 0, 0);
        try {
            Memory m = new Memory(this);
            org.json.JSONObject st = m.stats();
            mem.setText("记忆 " + st.optInt("key", 0) + " 条 · 日常 "
                    + st.optInt("daily", 0) + " 条");
        } catch (Throwable ignored) {
        }
        root.addView(mem);

        sc.addView(root);
        setContentView(sc);
    }

    private TextView label(String s) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(13);
        tv.setTextColor(Theme.muted());
        tv.setPadding(dp(2), 0, 0, dp(10));
        return tv;
    }

    private GradientDrawable inputBox() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(14));
        g.setColor(Theme.dark ? 0xFF1C1C1E : 0xFFFFFFFF);
        g.setStroke(dp(1), Theme.dark ? 0xFF2E2E31 : 0xFFE2E2E6);
        return g;
    }

    private void testKey() {
        String k = cleanKey(keyInput.getText().toString());
        if (k.isEmpty()) {
            setStatus("先填 Key", false);
            return;
        }
        setStatus("测试中…", null);
        new Thread(() -> {
            String r = DeepSeek.testKey(k);
            ui.post(() -> {
                if (r.startsWith("ok|")) {
                    setStatus("通了 · " + r.substring(3), true);
                } else {
                    setStatus(r.replace("fail|", "不通 · "), false);
                }
            });
        }, "test-key").start();
    }

    private void save() {
        String k = cleanKey(keyInput.getText().toString());
        if (k.isEmpty()) {
            setStatus("还没有填 Key", false);
            return;
        }
        String n = nameInput.getText().toString().trim();
        if (n.isEmpty()) {
            n = "大肥鱼";
        }
        prefs().edit()
                .putString(MainActivity.K_APIKEY, k)
                .putString(MainActivity.K_NAME, n)
                .apply();
        setStatus("保存好了，正在进入…", true);
        ui.postDelayed(() -> {
            startActivity(new android.content.Intent(this, MainActivity.class));
            finish();
        }, 350);
    }

    private void setStatus(String s, Boolean ok) {
        status.setText(s);
        if (ok == null) {
            status.setTextColor(Theme.muted());
        } else if (ok) {
            status.setTextColor(0xFF3BC46A);
        } else {
            status.setTextColor(0xFFE05B5B);
        }
    }

    private static String cleanKey(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("[\\s\\u200B\\uFEFF]", "");
    }
}
