package com.dafeiyu.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 独立版主界面 + 设置抽屉（用老版 SettingsDrawer）
 *
 * 跟老版的区别：
 *   × 不连网桥，直接调 DeepSeek
 *   √ 界面完全一样：顶栏、抽屉、卡片、气泡
 */
public class MainActivity extends Activity {

    public static final String PREF = "xiaoman_standalone";
    public static final String K_APIKEY = "apikey";
    public static final String K_NAME = "her_name";
    public static final String K_SKIN = "skin";
    public static final String K_THEME = "theme_dark";
    public static final String K_STATUS = "status_bar_on";
    private static final int REQ_PERSONA = 0x51;

    private static final int REQ_AVATAR = 3001;
    private static final int REQ_MY_AVATAR = 3004;
    private static final int REQ_ARCHIVE_BG = 3005;
    private static final int REQ_COVER = 3002;
    private static final int REQ_STICKER_ADD = 3003;

    private Chat chat;
    private DeepSeek ds;
    private Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout rootBox;
    private LinearLayout rootLayout;
    private LinearLayout inputBar;
    private LinearLayout msgBox;
    private ScrollView scroll;
    private EditText input;
    private TextView sendBtn;
    private TextView nameView;
    private TextView dotView;
    private TextView statusBar;
    private ImageView avatarView;
    private SettingsDrawer drawer;
    private StickerPanel stickers;

    private Bubble streamingBubble = null;
    private StringBuilder streamingText = new StringBuilder();
    private boolean sending = false;
    /** 你最近发的那张表情，防她原样回一遍 */
    private String lastUserSticker = null;
    /** 表情识别正在跑，防重入（否则并发识别会重复计费） */
    private volatile boolean visionRunning = false;
    private String lastSentText = "";
    private long lastSentAt = 0;
    private long tokenIn = 0, tokenOut = 0;

    private static class Bubble { TextView tv; Bubble(TextView t) { tv = t; } }

    // ================= 生命周期 =================
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Theme.init(this);
        // 读上次选的配色（没选过就用默认深蓝）
        Theme.dark = prefs().getBoolean(K_THEME, Theme.dark);
        Theme.skin = prefs().getInt(K_SKIN, 0);

        String key = prefs().getString(K_APIKEY, "");
        if (key.isEmpty()) startActivity(new Intent(this, SetupActivity.class));

        // 一次性清理：旧版「AI 分析并提取偏好」写进 user.md 的内容。
        // 那个入口已经删了，留着还会每轮塞进 prompt，白花 token。
        try {
            if (!prefs().getBoolean("migrated_user_track", false)) {
                new Memory(this).clearTrack(Memory.USER);
                prefs().edit().putBoolean("migrated_user_track", true).apply();
            }
        } catch (Throwable ignored) {}

        chat = new Chat(this);
        ds = new DeepSeek();
        buildUI();
        redraw();

        // 全局字体（行楷）
        try {
            FontKit.apply(this);
        } catch (Throwable ignored) {}


    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent == null) {
            return;
        }
        // 从通知/外部进来：重读磁盘刷新一下就行
        ui.postDelayed(() -> {
            try {
                chat = new Chat(this);
            } catch (Throwable ignored) {
            }
            redraw();
        }, 300);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (prefs().getString(K_APIKEY, "").isEmpty()) {
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }

        // 重读磁盘：别的会话或后台写入可能更新了历史，内存里这份要跟上。
        //
        // 但如果此刻正在等回复（流式还没结束），换 Chat 会让回调写到旧实例上，
        // sending 卡在 true 就再也发不出消息了。所以先把发送状态复位。
        try {
            if (sending) {
                sending = false;
                streamingBubble = null;
                streamingText.setLength(0);
            }
            chat = new Chat(this);
        } catch (Throwable ignored) {}
        try {
            if (sendBtn != null) {
                sendBtn.setAlpha(1f);
                sendBtn.setEnabled(true);
            }
        } catch (Throwable ignored) {}

        if (nameView != null) nameView.setText(herName());
        applyAvatar();
        applyBg();
        applyStatusBar();
        refreshStatusLine();
        redraw();
        /* removed */

        // 用文件管理器拖进来的新表情，回到 App 时自动认一次
        checkUndescribedStickers();
    }

    private SharedPreferences prefs() { return getSharedPreferences(PREF, Context.MODE_PRIVATE); }
    private String apiKey() { return prefs().getString(K_APIKEY, ""); }
    private String herName() {
        String n = prefs().getString(K_NAME, "");
        return (n == null || n.isEmpty()) ? "大肥鱼" : n;
    }
    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    // ================= 界面 =================
    private void buildUI() {
        rootBox = new FrameLayout(this);
        rootBox.setBackground(Theme.background());

        LinearLayout root = new LinearLayout(this);
        rootLayout = root;
        root.setOrientation(LinearLayout.VERTICAL);

        // ===== 顶栏 =====
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(14), dp(10), dp(12), dp(10));
        top.setBackgroundColor(Theme.bar());

        avatarView = new ImageView(this);
        avatarView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        android.graphics.drawable.GradientDrawable cir = new android.graphics.drawable.GradientDrawable();
        cir.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        cir.setColor(Theme.dark ? 0xFF2A3340 : 0xFFDCEAF5);
        avatarView.setBackground(cir);
        LinearLayout.LayoutParams avlp = new LinearLayout.LayoutParams(dp(38), dp(38));
        avlp.rightMargin = dp(11);
        top.addView(avatarView, avlp);

        // 点头像 = 切换聊天（微信也是这样）
        avatarView.setClickable(true);
        avatarView.setOnClickListener(v -> showSessions());

        LinearLayout meta = new LinearLayout(this);
        meta.setOrientation(LinearLayout.VERTICAL);
        top.addView(meta, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        meta.setClickable(true);
        meta.setOnClickListener(v -> showSessions());

        LinearLayout nameRow = new LinearLayout(this);
        nameRow.setOrientation(LinearLayout.HORIZONTAL);
        nameRow.setGravity(Gravity.CENTER_VERTICAL);
        meta.addView(nameRow);

        nameView = new TextView(this);
        nameView.setText(herName());
        nameView.setTextSize(16);
        nameView.setTextColor(Theme.text());
        nameRow.addView(nameView);

        dotView = new TextView(this);
        dotView.setText("●");
        dotView.setTextSize(9);
        dotView.setTextColor(0xFF4CAF50);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.leftMargin = dp(6);
        nameRow.addView(dotView, dlp);

        statusBar = new TextView(this);
        statusBar.setTextSize(11.5f);
        statusBar.setTextColor(Theme.muted());
        meta.addView(statusBar);
        refreshStatusLine();

        // 新建会话：气泡 + 加号（意思最准确，也好看）
        TextView newBtn = new TextView(this) {
            @Override
            protected void onDraw(android.graphics.Canvas cv) {
                super.onDraw(cv);
                float w = getWidth(), h = getHeight();
                float cx = w / 2f, cy = h * 0.47f;
                float r = Math.min(w, h) * 0.26f;

                android.graphics.Paint p2 = new android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG);
                p2.setColor(Theme.dark ? 0xFFE4ECF4 : 0xFF1B1F24);
                p2.setStyle(android.graphics.Paint.Style.FILL);
                cv.drawCircle(cx, cy, r, p2);

                // 左下角的小尾巴
                android.graphics.Path tail = new android.graphics.Path();
                tail.moveTo(cx - r * 0.60f, cy + r * 0.62f);
                tail.lineTo(cx - r * 0.98f, cy + r * 1.16f);
                tail.lineTo(cx - r * 0.10f, cy + r * 0.88f);
                tail.close();
                cv.drawPath(tail, p2);

                // 白色加号
                android.graphics.Paint wp = new android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG);
                wp.setColor(Theme.dark ? 0xFF1C1C1E : 0xFFFFFFFF);
                wp.setStrokeWidth(dp(2));
                wp.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                float a = r * 0.44f;
                cv.drawLine(cx - a, cy, cx + a, cy, wp);
                cv.drawLine(cx, cy - a, cx, cy + a, wp);
            }
        };
        newBtn.setFocusable(false);
        newBtn.setFocusableInTouchMode(false);
        newBtn.setClickable(true);
        newBtn.setOnClickListener(v -> onNewSession());
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(40), dp(40));
        nlp.leftMargin = dp(2);
        nlp.rightMargin = dp(2);
        top.addView(newBtn, nlp);

        // 菜单按钮（开抽屉）
        TextView menu = new TextView(this);
        menu.setText("☰");
        menu.setTextSize(20);
        menu.setTextColor(Theme.text());
        menu.setPadding(dp(10), dp(4), dp(4), dp(4));
        menu.setOnClickListener(v -> openDrawer());
        top.addView(menu);

        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View div = new View(this);
        div.setBackgroundColor(Theme.line());
        root.addView(div, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        // ===== 消息区 =====
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        msgBox = new LinearLayout(this);
        msgBox.setOrientation(LinearLayout.VERTICAL);
        msgBox.setPadding(dp(14), dp(12), dp(14), dp(12));
        scroll.addView(msgBox, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // ===== 输入栏 =====
        LinearLayout bar = new LinearLayout(this);
        inputBar = bar;
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(12), dp(12));
        bar.setBackgroundColor(Theme.bar());

        input = new EditText(this);
        input.setHint("说点什么…");
        input.setTextSize(15);
        input.setMaxLines(4);
        input.setHintTextColor(Theme.muted());
        input.setTextColor(Theme.text());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setBackground(Theme.inputBg());
        input.setPadding(dp(16), dp(11), dp(16), dp(11));
        bar.addView(input, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 表情包按钮
        TextView emojiBtn = new TextView(this);
        emojiBtn.setText("☺");
        emojiBtn.setTextSize(22);
        emojiBtn.setGravity(Gravity.CENTER);
        emojiBtn.setTextColor(Theme.muted());
        emojiBtn.setFocusable(false);
        emojiBtn.setFocusableInTouchMode(false);
        emojiBtn.setOnClickListener(v -> showStickers());
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(dp(40), dp(46));
        elp.leftMargin = dp(4);
        bar.addView(emojiBtn, elp);

        // 自绘纸飞机（不用字体字符，任何字体下都居中）
        sendBtn = new TextView(this) {
            @Override
            protected void onDraw(android.graphics.Canvas cv) {
                super.onDraw(cv);
                float w = getWidth(), h = getHeight();
                float cx = w / 2f, cy = h / 2f;
                float sz = Math.min(w, h) * 0.30f;
                android.graphics.Paint p2 = new android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG);
                p2.setColor(0xFFFFFFFF);
                p2.setStyle(android.graphics.Paint.Style.FILL);
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(cx, cy - sz);
                path.lineTo(cx + sz * 0.82f, cy + sz * 0.72f);
                path.lineTo(cx, cy + sz * 0.30f);
                path.lineTo(cx - sz * 0.82f, cy + sz * 0.72f);
                path.close();
                cv.drawPath(path, p2);
            }
        };
        // 关键：不要让按钮抢走输入框的焦点，否则发完消息键盘就收了
        sendBtn.setFocusable(false);
        sendBtn.setFocusableInTouchMode(false);
        sendBtn.setClickable(true);
        sendBtn.setTextSize(18);
        sendBtn.setGravity(Gravity.CENTER);
        sendBtn.setTextColor(0xFFFFFFFF);
        sendBtn.setBackground(Theme.sendButton());
        sendBtn.setOnClickListener(v -> doSend());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(46), dp(46));
        blp.leftMargin = dp(9);
        bar.addView(sendBtn, blp);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams rootLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        rootBox.addView(root, rootLp);

        // 点击消息区空白处 → 收键盘（点气泡本身不触发，因为气泡是子视图）
        scroll.setOnTouchListener((v, ev) -> {
            if (ev.getAction() == android.view.MotionEvent.ACTION_UP) hideKeyboard();
            return false;
        });
        // 顶栏空白也收
        top.setOnClickListener(v -> hideKeyboard());
        msgBox.setOnClickListener(v -> hideKeyboard());

        setContentView(rootBox);
        setupKeyboardFollow();
    }

    /** 键盘弹起时滚到底；不要碰输入框的焦点（之前那版把焦点滚掉了） */
    private void setupKeyboardFollow() {
        if (scroll == null) return;
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) {
                ui.post(this::scrollDownQuiet);
                ui.postDelayed(this::scrollDownQuiet, 120);
            }
        });
        // 只有在用户手指真的点进输入框时才保证键盘出来
        if (input != null) {
            input.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    if (stickers != null && stickers.isShown()) stickers.hide();
                    ui.post(this::showKeyboard);
                }
            });
            // 点输入框也收表情（有时候焦点没变，只是点了）
            input.setOnClickListener(v -> {
                if (stickers != null && stickers.isShown()) stickers.hide();
            });
        }
    }

    private boolean keyboardShown = false;

    /** 滚到底，但不动输入框焦点 */
    private void scrollDownQuiet() {
        try {
            if (scroll == null) return;
            boolean inputHadFocus = input != null && input.hasFocus();
            scroll.post(() -> {
                try {
                    scroll.scrollTo(0, Math.max(0,
                            msgBox.getMeasuredHeight() - scroll.getHeight()));
                } catch (Throwable ignored) {}
            });
            // 如果原来有焦点，滚完还回去
            if (inputHadFocus && input != null) {
                ui.post(() -> { if (!input.hasFocus()) input.requestFocus(); });
            }
        } catch (Throwable ignored) {}
    }

    /**
     * 把焦点和键盘留在输入框上。
     *
     * 发完消息后要让用户能直接继续打字，不用再点一下。
     * 用两次（立刻 + 下一帧）是因为 addBubble / scrollDown 可能在
     * 布局过程中把焦点抢走。
     */
    private void keepFocus() {
        try {
            if (input == null) {
                return;
            }
            input.requestFocus();
            input.postDelayed(() -> {
                try {
                    if (input != null && !input.hasFocus()) {
                        input.requestFocus();
                    }
                    if (input != null) {
                        android.view.inputmethod.InputMethodManager imm =
                                (android.view.inputmethod.InputMethodManager)
                                        getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null && !keyboardShown) {
                            imm.showSoftInput(input,
                                    android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                            keyboardShown = true;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }, 60);
        } catch (Throwable ignored) {
        }
    }

    private void showKeyboard() {
        try {
            if (input == null) return;
            input.requestFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                keyboardShown = true;
            }
        } catch (Throwable ignored) {}
    }

    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null && input != null) {
                imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
            }
            if (input != null) input.clearFocus();
            if (rootLayout != null) rootLayout.requestFocus();
        } catch (Throwable ignored) {}
    }

    private void refreshStatusLine() {
        if (statusBar == null) return;
        String[] on = statusFieldState();
        StringBuilder sb = new StringBuilder();
        try {
            Memory m = new Memory(this);
            JSONObject o = m.stats();
            if ("1".equals(on[0])) sb.append("记忆 ").append(o.optInt("total")).append(" 条");
        } catch (Exception ignored) {}
        if ("1".equals(on[1])) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("↑").append(tokenIn).append(" ↓").append(tokenOut);
        }
        if ("1".equals(on[2])) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append("历史 ").append(chat.history().length()).append(" 条");
        }
        if (sb.length() == 0) sb.append("直连 DeepSeek");
        else sb.append(" · 直连");
        statusBar.setText(sb.toString());
    }

    // ================= 抽屉 =================
    private boolean drawerAdded = false;

    private void openDrawer() {
        // 已经开着就先关掉，等它彻底关了再重建
        if (drawer != null) {
            final SettingsDrawer old = drawer;
            drawer = null;
            try {
                old.getView().animate().alpha(0f).setDuration(120).withEndAction(() -> {
                    try {
                        View ov = old.getView();
                        if (ov.getParent() instanceof ViewGroup) {
                            ((ViewGroup) ov.getParent()).removeView(ov);
                        }
                    } catch (Throwable ignored) {}
                    buildAndShowDrawer();
                }).start();
                return;
            } catch (Throwable ignored) {}
        }
        buildAndShowDrawer();
    }

    private void buildAndShowDrawer() {
        try {
            drawer = new SettingsDrawer(this, hostImpl);
            View dv = drawer.getView();
            rootBox.addView(dv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            dv.bringToFront();
            drawerAdded = true;
            drawer.show();
        } catch (Throwable e) {
            toast("设置打不开：" + e.getMessage());
        }
    }

    // ================= Host 实现 =================
    private final SettingsDrawer.Host hostImpl = new SettingsDrawer.Host() {
        public String getHost() { return "独立直连"; }
        public String getPort() { return ""; }
        public String getToken() { return apiKey(); }

        public String getHerName() { return MainActivity.this.herName(); }
        public void setHerName(String n) {
            prefs().edit().putString(K_NAME, n).apply();
            if (nameView != null) nameView.setText(n);
        }

        public int getSkin() { return prefs().getInt(K_SKIN, 0); }
        public void setSkin(int idx) {
            prefs().edit().putInt(K_SKIN, idx).apply();
            Theme.skin = idx;
            redraw();
        }

        public void onChangeAvatar() { pickImage(REQ_AVATAR); }
        public void onMyAvatar() { pickImage(REQ_MY_AVATAR); }

        public void onDescribeStickers() {
            try {
                java.util.List<String> todo = StickerCatalog.undescribed(MainActivity.this);
                if (todo.isEmpty()) {
                    MDialog.card(MainActivity.this, "认识表情包",
                            "所有表情都已经认识过了。\n\n新加的表情会自动识别。",
                            "知道了", null, null, null).show();
                    return;
                }
                MDialog.card(MainActivity.this, "认识表情包",
                        "有 " + todo.size() + " 张还没认过。\n\n"
                                + "让她看一眼，每张生成一句描述（约 0.0008 元/张）。\n"
                                + "认过就存下来，以后不用再花。",
                        "开始认", () -> {
                            if (drawer != null) drawer.hide();
                            toast("正在看…");
                            describeNewStickers();
                        }, "算了", null).show();
            } catch (Throwable t) {
                toast("出错：" + t);
            }
        }
        public void onCover() { pickImage(REQ_COVER); }



        public void onSessions() { showSessions(); }

        public void onExportChat() { exportAll(); }
        public void onImport() { importAll(); }

        // ---- 新入口 ----
        public void onArchive() {
            try {
                ChatArchive.open(MainActivity.this, chat);
            } catch (Throwable t) {
                toast("打不开：" + t);
            }
        }

        public void onSearch() {
            try {
                ChatSearch.open(MainActivity.this, chat);
            } catch (Throwable t) {
                toast("打不开：" + t);
            }
        }

        public void onPersonaLib() {
            try {
                PersonaLibrary.pick(MainActivity.this, chat);
            } catch (Throwable t) {
                toast("打不开：" + t);
            }
        }

        public void onGraph() {
            try {
                MemGraphPage.show(MainActivity.this);
            } catch (Throwable t) {
                toast("打不开：" + t);
            }
        }

        public void onAnalyze() { doAnalyze(); }
        public void onBalance() {
            try {
                BalanceChecker.show(MainActivity.this, apiKey());
            } catch (Throwable t) {
                toast("打不开：" + t);
            }
        }

        public void onTokenStats() {
            MDialog.card(MainActivity.this, "Token 统计",
                    "本轮输入约 " + tokenIn + " · 输出约 " + tokenOut +
                            "\n历史 " + chat.history().length() + " 条",
                    "知道了", null, null, null).show();
        }


        public void onPersona() {
            EditText e = new EditText(MainActivity.this);
            e.setText(chat.currentPersona());
            e.setTextSize(13);
            e.setSingleLine(false);
            e.setGravity(Gravity.TOP | Gravity.START);
            e.setTextColor(Theme.text());
            e.setBackground(Theme.inputBg());
            e.setPadding(dp(12), dp(10), dp(12), dp(10));
            ScrollView sw = new ScrollView(MainActivity.this);
            sw.addView(e);
            sw.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(360)));
            LinearLayout box = new LinearLayout(MainActivity.this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.addView(sw);
            box.addView(personaTools(e));

            MDialog.card(MainActivity.this, "编辑人格",
                    "改完立刻生效，下一句就按新的人格说。", box,
                    "保存", () -> {
                        if (chat.setPersona(e.getText().toString())) toast("存好了");
                        else toast("存不下（不能是空的）");
                    }, "算了", null).show();
        }

        /** 人格：导入 / 导出 / 恢复内置 三个小按钮 */
        private View personaTools(EditText e) {
            LinearLayout bar = new LinearLayout(MainActivity.this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setPadding(0, dp(8), 0, 0);

            TextView imp = miniBtn("导入 .md");
            TextView exp = miniBtn("导出");
            TextView rst = miniBtn("恢复内置");

            imp.setOnClickListener(v -> pickPersonaFile());
            exp.setOnClickListener(v -> exportPersona());
            rst.setOnClickListener(v -> {
                chat.resetPersona();
                e.setText(chat.persona());
                toast("已恢复内置人格");
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            bar.addView(imp, lp);
            bar.addView(exp, lp);
            bar.addView(rst, lp);
            return bar;
        }

        private TextView miniBtn(String s) {
            TextView t = new TextView(MainActivity.this);
            t.setText(s);
            t.setTextSize(13f);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(10), dp(9), dp(10), dp(9));
            t.setTextColor(Theme.accent());
            GradientDrawable g = new GradientDrawable();
            g.setCornerRadius(dp(12));
            g.setColor(Theme.dark ? 0x332A74AC : 0x1A4FB3A3);
            t.setBackground(g);
            return t;
        }



        public void onKeys() { toast("独立版没有钥匙功能"); }

        public void onAiConfig() { editKey(); }

        public void onTheme() {
            boolean d = prefs().getBoolean(K_THEME, Theme.dark);
            prefs().edit().putBoolean(K_THEME, !d).apply();
            Theme.dark = !d;
            recreate();
        }

        public void onStatusToggle() {
            boolean now = !prefs().getBoolean(K_STATUS, true);
            prefs().edit().putBoolean(K_STATUS, now).apply();
            applyStatusBar();
            toast(now ? "状态栏已开" : "状态栏已关");
        }

        public void onStatusFields() { showStatusFields(); }

        public void onMemories() { showMemory(); }


        public void onCompress() { compressNow(true); }

        public void onChatBg() { showBgPicker(); }

        public void onArchiveBg() { pickArchiveBg(); }

        public boolean isStatusOn() { return prefs().getBoolean(K_STATUS, true); }

        public void onClose() {
            if (drawer != null) drawer.hide();
        }
    };

    // ---------- 改 Key ----------
    private void editKey() {
        String cur = apiKey();
        // 密码框 + 眼睛切换，默认看不见，点眼睛才显示
        MDialog.inputSecret(this, "DeepSeek API Key",
                "sk- 开头的那串", cur, text -> {
                    try {
                        String val = text == null ? "" : text.trim();
                        if (val.startsWith("Bearer ")) {
                            val = val.substring(7).trim();
                        }
                        if (val.isEmpty()) {
                            return;
                        }
                        if (!val.startsWith("sk-")) {
                            toast("Key 应该是 sk- 开头，这个不对");
                            return;
                        }
                        prefs().edit().putString(K_APIKEY, val).apply();
                        toast("存好了");
                        if (drawer != null) {
                            drawer.hide();
                        }
                        ui.postDelayed(this::recreate, 250);
                    } catch (Throwable t) {
                        toast("存不下：" + t);
                    }
                }).show();
    }

    // ---------- 头像 / 背景 ----------
    private void pickImage(int req) {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i, "选一张图"), req); }
        catch (Exception e) { toast("打不开相册"); }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;

        // 加表情包走单独一条路（可能是多选）
        if (req == REQ_ARCHIVE_BG) {
            try {
                InputStream in = getContentResolver().openInputStream(data.getData());
                Bitmap bmp = BitmapFactory.decodeStream(in);
                in.close();
                if (bmp == null) {
                    toast("读不了这张图");
                    return;
                }
                int max = 1080;
                int w = bmp.getWidth(), h = bmp.getHeight();
                if (w > max || h > max) {
                    float sc = Math.min((float) max / w, (float) max / h);
                    bmp = Bitmap.createScaledBitmap(bmp, (int) (w * sc), (int) (h * sc), true);
                }
                File dst = new File(getFilesDir(), "archive_bg.png");
                FileOutputStream out = new FileOutputStream(dst);
                bmp.compress(Bitmap.CompressFormat.PNG, 92, out);
                out.flush();
                out.close();
                prefs().edit().putString("archive_bg", dst.getAbsolutePath()).apply();
                toast("列表背景换好了");
            } catch (Exception e) {
                toast("存不下：" + e.getMessage());
            }
            return;
        }

        if (req == REQ_STICKER_ADD) { importStickers(data); return; }
        if (req == REQ_PERSONA) { importPersonaFrom(data.getData()); return; }

        try {
            InputStream in = getContentResolver().openInputStream(data.getData());
            Bitmap bmp = BitmapFactory.decodeStream(in);
            in.close();
            if (bmp == null) { toast("读不了这张图"); return; }

            String fileName = "bg.png";
            int max = 1080;
            if (req == REQ_AVATAR) {
                fileName = "avatar.png";
                max = 256;
            } else if (req == REQ_MY_AVATAR) {
                fileName = "my_avatar.png";
                max = 256;
            }
            File dst = new File(getFilesDir(), fileName);
            int w = bmp.getWidth(), h = bmp.getHeight();
            if (w > max || h > max) {
                float sc2 = Math.min((float) max / w, (float) max / h);
                bmp = Bitmap.createScaledBitmap(bmp, (int) (w * sc2), (int) (h * sc2), true);
            }
            FileOutputStream out = new FileOutputStream(dst);
            bmp.compress(Bitmap.CompressFormat.PNG, 92, out);
            out.flush();
            out.close();

            if (req == REQ_AVATAR) {
                prefs().edit().putString("avatar", dst.getAbsolutePath()).apply();
            } else if (req == REQ_MY_AVATAR) {
                prefs().edit().putString("my_avatar", dst.getAbsolutePath()).apply();
            } else {
                prefs().edit().putString("bg", dst.getAbsolutePath())
                        .putString("chat_bg_preset", "").apply();
            }
            if (req == REQ_AVATAR) {
                applyAvatar();
            } else if (req == REQ_MY_AVATAR) {
                AvatarCache.invalidateAll();
                redraw();
            } else {
                applyBg();
            }
            toast("换好了");
        } catch (Exception e) { toast("存不下：" + e.getMessage()); }
    }

    private void applyAvatar() {
        try {
            if (avatarView == null) {
                return;
            }
            // 顶栏也走缓存 + 圆形裁剪
            android.graphics.Bitmap b = AvatarCache.get(this);
            if (b != null) {
                avatarView.setImageDrawable(new CircleImageDrawable(b));
            }
        } catch (Throwable ignored) {}
    }

    /** 手动导出：把所有数据打包到 /sdcard/Download/ 下一个文件夹 */
    /**
     * 导出全部数据。
     *
     * 写到 Download/大肥鱼备份/ —— 文件管理器能看到、能拷走。
     * 同时在 App 私有目录留一份，方便「导入数据」直接读。
     */
    private void exportAll() {
        try {
            final File pub = publicBackupDir();

            // 1. 先写到私有目录（导入用）
            File priv = new File(backupRoot(), "export");
            if (!priv.exists()) {
                priv.mkdirs();
            }
            writeBackupTo(priv);

            // 2. 复制到 Download（你能看到）
            writeBackupTo(pub);

            toast("导出好了：" + pub.getAbsolutePath());
        } catch (Throwable e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    /** 公共备份目录：Download/大肥鱼备份 */
    private File publicBackupDir() {
        File dl = android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        File dst = new File(dl, "大肥鱼备份");
        if (!dst.exists()) {
            dst.mkdirs();
        }
        return dst;
    }

    /** 把数据写到指定目录。 */
    private void writeBackupTo(File dst) {
        try {
            if (!dst.exists()) {
                dst.mkdirs();
            }

            // 会话目录（多会话的全部历史）
            Filer.copyDir(new File(getFilesDir(), "sessions"), new File(dst, "sessions"));
            // 老的单一历史（向后兼容）
            Filer.copyTo(new File(getFilesDir(), "history.json"), new File(dst, "history.json"));
            // 记忆
            Filer.copyDir(new File(getFilesDir(), "memory"), new File(dst, "memory"));
            // 人格
            Filer.copyTo(new File(getFilesDir(), "persona.md"), new File(dst, "persona.md"));
            // 头像 / 背景
            Filer.copyTo(new File(getFilesDir(), "avatar.png"), new File(dst, "avatar.png"));
            Filer.copyTo(new File(getFilesDir(), "my_avatar.png"), new File(dst, "my_avatar.png"));
            Filer.copyTo(new File(getFilesDir(), "bg.png"), new File(dst, "bg.png"));
            Filer.copyTo(new File(getFilesDir(), "archive_bg.png"), new File(dst, "archive_bg.png"));
            // 表情描述
            Filer.copyTo(new File(getFilesDir(), "sticker_names.json"),
                    new File(dst, "sticker_names.json"));

            // 设置（不含 API Key）
            java.util.Map<String, ?> all = prefs().getAll();
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
                String k = e.getKey();
                if (K_APIKEY.equals(k)) {
                    sb.append(k).append("=（已省略）\n");
                    continue;
                }
                sb.append(k).append("=").append(e.getValue()).append("\n");
            }
            Filer.writeString(new File(dst, "settings.txt"), sb.toString());

            // 说明
            Filer.writeString(new File(dst, "说明.txt"),
                    "大肥鱼备份\n"
                            + "导出时间：" + new java.text.SimpleDateFormat(
                            "yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .format(new java.util.Date()) + "\n\n"
                            + "包含内容：\n"
                            + "  sessions/          每个聊天的对话历史\n"
                            + "  memory/            记忆（user.md 等）\n"
                            + "  persona.md         人格\n"
                            + "  avatar.png         她的头像\n"
                            + "  my_avatar.png      你的头像\n"
                            + "  bg.png             聊天背景\n"
                            + "  archive_bg.png     历史记录背景\n"
                            + "  sticker_names.json 表情描述\n"
                            + "  settings.txt       设置（不含 API Key）\n\n"
                            + "恢复方法：把整个文件夹放回\n"
                            + "  Download/大肥鱼备份/\n"
                            + "然后在 App 里点「导入数据」。\n");
        } catch (Throwable ignored) {
        }
    }

    /** 从备份目录把数据导回来。 */
    private void importAll() {
        try {
            File src = null;

            // 1. 优先 Download/大肥鱼备份（你自己放回去的那份）
            try {
                File pub = publicBackupDir();
                if (pub.exists() && pub.listFiles() != null && pub.listFiles().length > 0) {
                    src = pub;
                }
            } catch (Throwable ignored) {
            }

            // 2. 老位置：App 私有目录 backup/export
            if (src == null) {
                File cand = new File(backupRoot(), "export");
                if (cand.exists()) {
                    src = cand;
                }
            }

            if (src == null) {
                SimpleDialog.alert(this, "导入数据",
                        "没找到备份。\n\n"
                                + "先点「导出全部数据」，备份会放在：\n"
                                + "  Download/大肥鱼备份/\n\n"
                                + "恢复时把那个文件夹原样放回去，再点这里。");
                return;
            }

            final File fsrc = src;
            StringBuilder have = new StringBuilder();
            File sess = new File(fsrc, "sessions");
            if (sess.exists()) {
                File[] sf = sess.listFiles();
                have.append("会话 ").append(sf == null ? 0 : sf.length).append(" 个\n");
            }
            File h = new File(fsrc, "history.json");
            if (h.exists()) {
                have.append("聊天记录 ").append(h.length() / 1024).append(" KB\n");
            }
            File mem = new File(fsrc, "memory");
            if (mem.exists()) {
                File[] fs = mem.listFiles();
                have.append("记忆 ").append(fs == null ? 0 : fs.length).append(" 个文件\n");
            }
            if (new File(fsrc, "avatar.png").exists()) {
                have.append("头像\n");
            }
            if (new File(fsrc, "bg.png").exists()) {
                have.append("背景\n");
            }

            MDialog.card(this, "导入数据",
                    "从 " + fsrc.getName() + " 导入，会覆盖当前的聊天记录和记忆。\n\n"
                            + "备份里有：\n" + have + "\n确定吗？",
                    "导入", () -> doImport(fsrc), "算了", null).show();
        } catch (Throwable e) {
            toast("导入失败：" + e.getMessage());
        }
    }

    private void doImport(File src) {
        try {
            int ok = 0;

            // 聊天记录
            File h = new File(src, "history.json");
            if (h.exists()) {
                Filer.copyTo(h, new File(getFilesDir(), "history.json"));
                ok++;
            }

            // 记忆
            File mem = new File(src, "memory");
            if (mem.exists()) {
                File dstMem = new File(getFilesDir(), "memory");
                if (!dstMem.exists()) {
                    dstMem.mkdirs();
                }
                Filer.copyDir(mem, dstMem);
                ok++;
            }

            // 头像 / 背景
            File av = new File(src, "avatar.png");
            if (av.exists()) {
                Filer.copyTo(av, new File(getFilesDir(), "avatar.png"));
                AvatarCache.invalidate();
                ok++;
            }
            File bg = new File(src, "bg.png");
            if (bg.exists()) {
                Filer.copyTo(bg, new File(getFilesDir(), "bg.png"));
                ok++;
            }

            // 重新加载
            chat = new Chat(this);
            redraw();
            applyBg();
            applyAvatar();

            toast(ok > 0 ? "导入好了，共 " + ok + " 项" : "备份里没有可导入的东西");
        } catch (Throwable e) {
            toast("导入失败：" + e.getMessage());
        }
    }

    /** 挑一个 .md 当人格 */
    private void pickPersonaFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try { startActivityForResult(Intent.createChooser(i, "选人格文件(.md/.txt)"), REQ_PERSONA); }
        catch (Exception e) { toast("打不开文件选择器"); }
    }

    /** 把当前人格导到外部目录，方便发给别人 */
    private void exportPersona() {
        try {
            File dir = getExternalFilesDir(null);
            if (dir == null) { toast("没外部目录"); return; }
            File out = new File(dir, "persona.md");
            Filer.writeString(out, chat.persona());
            toast("导好了：" + out.getAbsolutePath());
        } catch (Throwable e) {
            toast("导出失败：" + e.getMessage());
        }
    }

    /** 读 .md 文件塞进人格 */
    private void importPersonaFrom(Uri uri) {
        try {
            InputStream in = getContentResolver().openInputStream(uri);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            in.close();
            String text = new String(bos.toByteArray(), "UTF-8");
            if (text.trim().isEmpty()) { toast("这文件是空的"); return; }
            if (chat.setPersona(text)) {
                toast("人格换好了（" + text.length() + " 字）");
                recreate();
            } else {
                toast("存不下");
            }
        } catch (Throwable e) {
            toast("读不了：" + e.getMessage());
        }
    }

    /**
     * 备份根目录。
     * Android 13+ / MIUI 对 /sdcard/Android/data 的写入卡得很死，
     * 所以一律写私有目录：/data/data/包名/files/backup/（App 自己的地盘，一定能写）
     */
    private File backupRoot() {
        File fb = new File(getFilesDir(), "backup");
        if (!fb.exists()) fb.mkdirs();
        return fb;
    }



    /** 把摘要同步到备份目录 */
    private void syncSummaryOut() {
        try {
            File sf = new File(getFilesDir(), "summary.md");
            File base = backupRoot();
            if (base == null) {
                android.util.Log.e("FISH", "syncSummary: base null");
                return;
            }
            File bd = new File(base, "backup");
            if (!bd.exists() && !bd.mkdirs()) {
                android.util.Log.e("FISH", "syncSummary: mkdirs failed " + bd);
            }
            Filer.copyTo(sf, new File(bd, "summary-latest.md"));
            File out = new File(bd, "summary-latest.md");
            android.util.Log.i("FISH", "syncSummary srcExists=" + sf.exists()
                    + " size=" + sf.length()
                    + " outExists=" + out.exists()
                    + " outSize=" + out.length());
        } catch (Throwable e) {
            android.util.Log.e("FISH", "syncSummary fail", e);
        }
    }

    /** 手动压缩：把早期对话压成摘要 */
    private void compressNow(boolean manual) {
        String block = chat.pendingBlockForCompress(manual);
        android.util.Log.i("FISH", "compress manual=" + manual
                + " hist=" + chat.history().length()
                + " blockLen=" + (block == null ? -1 : block.length())
                + " summaryLen=" + chat.summary().length());
        if (block == null) {
            syncSummaryOut();
            if (manual) toast("没什么可压了（已有摘要 " + chat.summary().length() + " 字）");
            return;
        }
        final int upTo = chat.pendingCompressTo(manual);
        final int before = chat.summary().length();
        if (manual) toast("压缩中…");
        ui.post(() -> {});
        new Thread(() -> {
            String sum = DeepSeek.summarize(apiKey(), block);
            ui.post(() -> {
                if (sum == null) {
                    if (manual) toast("压缩失败（网络或 Key 问题）");
                    return;
                }
                android.util.Log.i("FISH", "summarize OK len=" + sum.length() + " upTo=" + upTo);
                String merged = chat.mergeSummary(sum, upTo);
                int after = merged.length();
                toast("压好了：" + before + " → " + after + " 字，"
                        + upTo + " 条已归档");
                syncSummaryOut();
                autoBackup();
                if (statusBar != null) applyStatusBar();
            });
        }).start();
    }

    /** 聊完自动检查一次 */
    private void maybeAutoCompress() {
        try {
            if (chat.shouldCompress()) {
                // 别打扰正在说话的，等它说完再压
                ui.postDelayed(() -> compressNow(false), 2500);
            }
        } catch (Throwable ignored) {}
    }

    /** 每次聊天后自动往外部目录写一份备份，卸载/崩溃都能救回来 */
    private void autoBackup() {
        try {
            File base = backupRoot();
            android.util.Log.i("FISH", "backup base=" + base);
            if (base == null) return;
            File dir = new File(base, "backup");
            if (!dir.exists()) dir.mkdirs();

            // 1. 聊天历史
            File hf = new File(getFilesDir(), "history.json");
            File mem = new File(getFilesDir(), "memory");
            java.text.SimpleDateFormat f =
                    new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US);
            String stamp = f.format(new java.util.Date());

            // 每次聊天都覆盖一份 latest，防住"最新那条"
            Filer.copyTo(hf, new File(dir, "history-latest.json"));
            // 摘要也备一份，能直接看到压缩结果
            try {
                File sf = new File(getFilesDir(), "summary.md");
                if (sf.exists()) Filer.copyTo(sf, new File(dir, "summary-latest.md"));
            } catch (Throwable ignored) {}
            Filer.copyDir(mem, new File(dir, "memory-latest"));

            // 每隔一段时间留一份带时间戳的
            android.util.Log.i("FISH", "backup done dir=" + dir.getAbsolutePath()
                    + " histExists=" + hf.exists()
                    + " memExists=" + mem.exists());
            long last = prefs().getLong("last_backup", 0L);
            if (System.currentTimeMillis() - last > 6 * 3600 * 1000L) {
                Filer.copyTo(hf, new File(dir, "history-" + stamp + ".json"));
                Filer.copyDir(mem, new File(dir, "memory-" + stamp));
                prefs().edit().putLong("last_backup", System.currentTimeMillis()).apply();
            }
        } catch (Throwable ignored) {}
    }

    /** 状态栏那一行显不显示（开关改了立刻生效，重启也记住） */
    private void applyStatusBar() {
        try {
            boolean on = prefs().getBoolean(K_STATUS, true);
            if (statusBar != null) {
                ui.post(() -> {
                    try {
                        statusBar.setVisibility(on ? View.VISIBLE : View.GONE);
                    } catch (Throwable ignored) {}
                });
            }
        } catch (Throwable ignored) {}
    }

    /** 把 res 里的图片解成 Bitmap（center-crop 用）。 */
    private Bitmap decodeResBitmap(int rid) {
        try {
            java.io.InputStream in = getResources().openRawResource(rid);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            try { in.close(); } catch (Throwable ignored) {}
            return b;
        } catch (Throwable t) {
            return null;
        }
    }

    private void applyBg() {
        try {
            // 先看有没有选内置背景
            String preset = prefs().getString("chat_bg_preset", "");
            if (!preset.isEmpty() && rootBox != null) {
                int rid = BgPicker.drawableId(this, preset);
                if (rid != 0) {
                    final Bitmap d = decodeResBitmap(rid);
                    if (d != null) {
                        ui.post(() -> {
                            try {
                                rootBox.setBackground(new BgDrawable(d));
                                if (scroll != null) scroll.setBackgroundColor(0x00000000);
                            } catch (Throwable ignored) {}
                        });
                        return;
                    }
                }
            }
            File f = new File(getFilesDir(), "bg.png");
            if (!f.exists()) return;
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (bmp == null || rootBox == null) return;
            final Bitmap bgBmp = bmp;
            ui.post(() -> {
                try {
                    // 等比裁切填满（center-crop），不拉伸变形
                    rootBox.setBackground(new BgDrawable(bgBmp));
                    if (scroll != null) scroll.setBackgroundColor(0x00000000);
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    /** 历史聊天记录列表的背景（单独设，没设就用聊天的）。 */
    private void pickArchiveBg() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "选一张列表背景"), REQ_ARCHIVE_BG);
        } catch (Exception e) {
            toast("打不开相册");
        }
    }

    // ---------- 导出 ----------
    private void exportChat() {
        try {
            File f = new File(getExternalFilesDir(null), "chat-" + Memory.today() + ".txt");
            StringBuilder sb = new StringBuilder();
            JSONArray h = chat.history();
            for (int i = 0; i < h.length(); i++) {
                JSONObject m = h.getJSONObject(i);
                boolean mine = "user".equals(m.optString("role"));
                sb.append(mine ? "他：" : herName() + "：").append(m.optString("content")).append("\n\n");
            }
            FileOutputStream out = new FileOutputStream(f);
            out.write(sb.toString().getBytes("UTF-8"));
            out.close();
            toast("导出到 " + f.getAbsolutePath());
        } catch (Exception e) { toast("导出失败：" + e.getMessage()); }
    }

    // ---------- 记忆 ----------
    private void showMemory() {
        try {
            MemoryPage.open(this);
        } catch (Throwable t) {
            toast("\u6253\u4E0D\u5F00\uFF1A" + t);
        }
    }

    // ================= 表情包 =================
    /** 聊天背景：九宫格内置图 + 从相册 */
    private BgPicker bgPicker;

    private void showBgPicker() {
        // 上次收起时把视图摘掉了，这里重新挂回去
        if (bgPicker != null && bgPicker.getView().getParent() == null) {
            rootBox.addView(bgPicker.getView(), bgPicker.defaultParams());
        }
        if (bgPicker == null) {
            bgPicker = new BgPicker(this, prefs().getString("chat_bg_preset", ""),
                    new BgPicker.OnPick() {
                        @Override
                        public void onPreset(String key) {
                            String resName = "";
                            for (String[] p : BgPicker.PRESETS) {
                                if (p[0].equals(key)) { resName = p[2]; break; }
                            }
                            prefs().edit().putString("chat_bg_preset", resName).apply();
                            applyBg();
                            toast("背景换好了");
                        }
                        @Override
                        public void onAlbum() { pickImage(REQ_COVER); }
                    });
            rootBox.addView(bgPicker.getView(), bgPicker.defaultParams());
        }
        // 关键：提到最上层，不然被设置抽屉压住看不见
        try {
            View v = bgPicker.getView();
            v.bringToFront();
            if (android.os.Build.VERSION.SDK_INT >= 21) v.setElevation(2000);
        } catch (Throwable ignored) {}
        bgPicker.show();
    }

    private void showStickers() {
        // 表情和键盘互斥：开表情先收键盘（跟微信一样）
        hideKeyboardNow();

        if (stickers == null) {
            stickers = new StickerPanel(this, asset -> sendSticker(asset));
            stickers.setOnAdd(() -> pickStickerImages());
            stickers.setAfterHide(() -> ui.postDelayed(this::showKeyboard, 80));
            rootBox.addView(stickers.getView(), new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        try {
            View sv = stickers.getView();
            sv.bringToFront();
            if (android.os.Build.VERSION.SDK_INT >= 21) sv.setElevation(1500);
        } catch (Throwable ignored) {}
        stickers.show();
    }

    /** 立刻收键盘：先卸焦点再收，最后把输入类型临时改成不可输入（对第三方输入法最硬） */
    private void hideKeyboardNow() {
        try {
            if (input == null) return;
            input.clearFocus();
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager)
                            getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
                imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
            }
            // 关键：临时置为不可输入，逼输入法关闭（很多第三方输入法只认这一下）
            input.setInputType(InputType.TYPE_NULL);
            input.setShowSoftInputOnFocus(false);
            ui.postDelayed(() -> {
                try {
                    if (input == null) return;
                    input.setInputType(InputType.TYPE_CLASS_TEXT
                            | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                    input.setShowSoftInputOnFocus(true);
                } catch (Throwable ignored) {}
            }, 300);
        } catch (Throwable ignored) {}
    }

    /** 从相册挑图加进表情包（可以一次挑好几张） */
    private void pickStickerImages() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(Intent.createChooser(i, "挑要加的表情"), REQ_STICKER_ADD);
        } catch (Exception e) {
            toast("打不开相册");
        }
    }

    /** 把选中的图存进 files/stickers/ */
    /** 找一个没被占用的编号，带指定扩展名。 */
    private File nextStickerFileExt(File dir, String ext) {
        for (int i = 1; i < 10000; i++) {
            File f = new File(dir, i + ext);
            // 同名但别的扩展名也算占用，避免 1.png 和 1.gif 冲突
            boolean busy = f.exists()
                    || new File(dir, i + ".png").exists()
                    || new File(dir, i + ".gif").exists()
                    || new File(dir, i + ".jpg").exists();
            if (!busy) {
                return f;
            }
        }
        return new File(dir, System.currentTimeMillis() + ext);
    }

    /** 找一个没被占用的编号：1.png、2.png… */
    private File nextStickerFile(File dir) {
        for (int i = 1; i < 10000; i++) {
            File f = new File(dir, i + ".png");
            if (!f.exists()) {
                return f;
            }
        }
        return new File(dir, System.currentTimeMillis() + ".png");
    }

    private void importStickers(Intent data) {
        try {
            java.util.List<android.net.Uri> uris = new java.util.ArrayList<>();
            android.content.ClipData clip = data.getClipData();
            if (clip != null) {
                for (int k = 0; k < clip.getItemCount(); k++) uris.add(clip.getItemAt(k).getUri());
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (uris.isEmpty()) return;

            File dir = StickerPanel.userDir(this);
            int ok = 0;
            int skipped = 0;
            for (android.net.Uri u : uris) {
                try {
                    // 先看原文件多大、什么格式
                    String mime = null;
                    String dispName = null;
                    try {
                        mime = getContentResolver().getType(u);
                        android.database.Cursor cur = getContentResolver().query(u,
                                new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
                        if (cur != null) {
                            try {
                                if (cur.moveToFirst()) {
                                    dispName = cur.getString(0);
                                }
                            } finally {
                                cur.close();
                            }
                        }
                    } catch (Throwable ignored) {}

                    boolean isGif = (mime != null && mime.contains("gif"))
                            || (dispName != null && dispName.toLowerCase().endsWith(".gif"));

                    if (isGif) {
                        // 动图：原样拷贝，不转码，动画才留得住
                        InputStream in = getContentResolver().openInputStream(u);
                        if (in == null) {
                            continue;
                        }
                        File dst = nextStickerFileExt(dir, ".gif");
                        FileOutputStream out = new FileOutputStream(dst);
                        byte[] buf = new byte[16384];
                        int n;
                        long total = 0;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            total += n;
                        }
                        in.close();
                        out.flush();
                        out.close();
                        // 太大的就不要了（超过 3MB）
                        if (total > 3L * 1024 * 1024) {
                            dst.delete();
                            skipped++;
                            continue;
                        }
                        ok++;
                        continue;
                    }

                    // 静态图：解码 → 缩到 512 内 → 转 png（省空间）
                    InputStream in = getContentResolver().openInputStream(u);
                    Bitmap bmp = BitmapFactory.decodeStream(in);
                    if (in != null) {
                        in.close();
                    }
                    if (bmp == null) {
                        skipped++;
                        continue;
                    }
                    int w = bmp.getWidth(), h = bmp.getHeight(), max = 512;
                    if (w > max || h > max) {
                        float sc = Math.min((float) max / w, (float) max / h);
                        bmp = Bitmap.createScaledBitmap(bmp, (int) (w * sc), (int) (h * sc), true);
                    }
                    File dst = nextStickerFileExt(dir, ".png");
                    FileOutputStream out = new FileOutputStream(dst);
                    bmp.compress(Bitmap.CompressFormat.PNG, 92, out);
                    out.flush();
                    out.close();
                    ok++;
                } catch (Exception ignored) {
                    skipped++;
                }
            }
            if (ok > 0) {
                toast(skipped > 0 ? "加了 " + ok + " 张（" + skipped + " 张读不了）"
                        : "加了 " + ok + " 张");
                if (stickers != null) stickers.refresh();
                // 让视觉模型看一眼，生成描述存起来（一次识别，永久有效）
                describeNewStickers();
            } else {
                toast("一张都没读进来");
            }
        } catch (Exception e) {
            toast("导入失败：" + e.getMessage());
        }
    }

    /** 有没有还没认过的表情，有就自动认。防抖：10 分钟内不重复触发。 */
    private void checkUndescribedStickers() {
        try {
            if (!hasApiKey()) {
                return;
            }
            long last = prefs().getLong("last_sticker_vision", 0);
            if (System.currentTimeMillis() - last < 10 * 60 * 1000L) {
                return;
            }
            java.util.List<String> todo = StickerCatalog.undescribed(this);
            if (todo.isEmpty()) {
                return;
            }
            prefs().edit().putLong("last_sticker_vision", System.currentTimeMillis()).apply();
            describeNewStickers();
        } catch (Throwable ignored) {
        }
    }

    private boolean hasApiKey() {
        try {
            return !apiKey().trim().isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 给还没识别过的表情生成描述。
     *
     * 用视觉模型看一眼，结果存到 sticker_names.json。
     * 之后她读文字就知道每张是什么，不用每次发图都花 token。
     */
    private void describeNewStickers() {
        if (visionRunning) {
            return;      // 已经有一批在认了，别重复发请求
        }
        visionRunning = true;
        new Thread(() -> {
            int done = 0, fail = 0;
            try {
                java.util.List<String> todo = StickerCatalog.undescribed(this);
                if (todo.isEmpty()) {
                    return;
                }
                File dir = StickerPanel.userDir(this);
                for (String name : todo) {
                    File f = new File(dir, name);
                    if (!f.isFile()) {
                        continue;
                    }
                    String d = StickerVision.describe(this, f);
                    if (d != null && !d.trim().isEmpty()) {
                        StickerCatalog.saveUserDesc(this, name, d);
                        done++;
                        ui.post(() -> {
                            try {
                                if (stickers != null) stickers.refresh();
                            } catch (Throwable ignored) {}
                        });
                    } else {
                        fail++;
                    }
                }
            } catch (Throwable ignored) {
            }
            visionRunning = false;
            final int fd = done, ff = fail;
            ui.post(() -> {
                if (fd > 0) {
                    toast("认出 " + fd + " 张表情的内容");
                } else if (ff > 0) {
                    String why = StickerVision.lastError;
                    toast("有 " + ff + " 张没认出来"
                            + (why == null ? "" : "：" + why));
                }
            });
        }, "sticker-vision").start();
    }

    /** 发一张表情：记成 [[stickers/xxx.png]] 或 [[user/xxx.png]]，画的时候还原成图 */
    private void sendSticker(String asset) {
        // 屏幕上是图
        addOneBubble("[[" + asset + "]]", true);

        // 历史里存标记 —— 这样熄屏重开、切后台回来，redraw 还能把图恢复出来。
        // （之前存成人话，重绘就只剩一行文字，表情全丢了）
        String mark = "[[" + asset + "]]";
        chat.append("user", mark, System.currentTimeMillis());

        // 但发给模型时要转成人话，不然她看到的是一串乱码
        doSendOnly(stickerPlainName(asset));
    }

    /** 把 [[user/困困.png]] 变成「（我发了个表情：困困）」。 */
    /**
     * 把 [[user/1.png]] 变成她能看懂的一句话。
     *
     * 原来只给文件名（「我发了个表情：1」），她完全不知道是啥图，
     * 只能回「这是啥 我看不懂」。现在尽量给描述。
     */
    private String stickerPlainName(String asset) {
        try {
            String a = asset;
            if (a.startsWith("user/")) {
                a = a.substring(5);
            } else if (a.startsWith("stickers/")) {
                a = a.substring(9);
            }
            String ext = "";
            int dot = a.lastIndexOf('.');
            if (dot > 0) {
                ext = a.substring(dot);
                a = a.substring(0, dot);
            }

            // 1) 有描述就用描述（assets 里那 167 张表）
            String desc = null;
            try {
                desc = StickerCatalog.descOf(a);
            } catch (Throwable ignored) {}

            if (desc != null && !desc.trim().isEmpty()) {
                return "（我发了个表情：" + desc.trim() + "）";
            }

            // 2) 名字本身有意义（不是纯数字）就直接用
            if (!a.matches("\\d+")) {
                return "（我发了个表情：" + a + "）";
            }

            // 3) 纯数字（1.png 这种）：带扩展名一起给她，至少能看出格式
            return "（我发了一张表情图：" + a + (ext.isEmpty() ? "" : ext) + "）";
        } catch (Throwable t) {
            return "（我发了个表情）";
        }
    }

    // ---------- 画消息 ----------
    private void redraw() {
        msgBox.removeAllViews();
        sanitizeHistoryOnce();
        JSONArray h = chat.history();
        if (h.length() == 0) {
            // 还没说过话：什么都不显示（之前这里会写一行「（还没说过话）」）
            return;
        }
        for (int i = 0; i < h.length(); i++) {
            try {
                JSONObject m = h.getJSONObject(i);
                addBubble(m.optString("content", ""), "user".equals(m.optString("role")));
            } catch (Exception ignored) {}
        }
        // 气泡是新建的，得再刷一次字体
        try {
            FontKit.apply(this);
        } catch (Throwable ignored) {}
        scrollDown();
    }

    /**
     * 一条消息可能有好几行，一行画一个气泡 —— 跟老版一样。
     * 她本来就是一短句一行写的，挤在一个气泡里就不像在聊天了。
     */
    private Bubble addBubble(String text, boolean mine) {
        Bubble last = null;
        boolean any = false;
        // 太长的句子切开，一个气泡一句，读起来才像聊天
        String prepared = text;
        try {
            if (!mine) {
                prepared = Sanitize.splitLines(text);
            }
        } catch (Throwable ignored) {}

        String[] lines = (prepared == null ? "" : prepared).split("\n");
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            Bubble b = addOneBubble(t, mine);
            if (b != null) {
                last = b;
                any = true;
            }
        }

        // 一行都没画出来（比如全是空行，或者她回了个重复表情被拦了）：
        // 只有真的一个字都没有时才兜底画一句，避免重复触发拦截
        if (!any) {
            String fallback = text == null ? "" : text.trim();
            if (!fallback.isEmpty()) {
                last = addOneBubble(fallback, mine);
            }
        }
        return last;
    }

    /**
     * 从一段文字里提取表情文件名。
     * 支持 ${s001.png}、${user/xxx.png}、[[stickers/x.png]]、[[user/x.png]]。
     * 不是表情就返回 null。
     */
    private String stickerNameIn(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.isEmpty() || t.length() > 140) {
            return null;
        }

        String inner = null;
        if (t.startsWith("${") && t.endsWith("}")) {
            inner = t.substring(2, t.length() - 1).trim();
        } else if (t.startsWith("[[") && t.endsWith("]]")) {
            inner = t.substring(2, t.length() - 2).trim();
        }
        if (inner == null || inner.isEmpty()) {
            return null;
        }

        // 已经带前缀、且文件真实存在
        if (inner.startsWith("stickers/")) {
            return existsInAssets(inner.substring(9)) ? inner : null;
        }
        if (inner.startsWith("user/")) {
            return existsInUser(inner.substring(5)) ? inner : null;
        }

        // 裸名字：模型现在只给不带扩展名的名字，所以要把扩展名补回来试
        String[] exts = {null, ".png", ".gif", ".jpg", ".jpeg", ".webp"};
        for (String ext : exts) {
            String cand = ext == null ? inner : inner + ext;
            if (existsInUser(cand)) {
                return "user/" + cand;
            }
            if (existsInAssets(cand)) {
                return "stickers/" + cand;
            }
        }

        // 再宽松一点：名字对不上，但去掉扩展名后相等（大小写也不管）
        String stem = inner;
        int dot = stem.lastIndexOf('.');
        if (dot > 0) {
            stem = stem.substring(0, dot);
        }
        try {
            java.io.File ud = StickerPanel.userDir(this);
            if (ud != null) {
                java.io.File[] fs = ud.listFiles();
                if (fs != null) {
                    for (java.io.File f : fs) {
                        String n = f.getName();
                        String ns = n;
                        int d2 = ns.lastIndexOf('.');
                        if (d2 > 0) {
                            ns = ns.substring(0, d2);
                        }
                        if (ns.equalsIgnoreCase(stem)) {
                            return "user/" + n;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return null;
    }

    /** 资产目录里有这张吗（含补扩展名）。 */
    private boolean existsInAssets(String name) {
        try {
            String[] all = getAssets().list("stickers");
            if (all == null) {
                return false;
            }
            for (String n : all) {
                if (n.equals(name)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /** 用户表情目录里有这张吗。 */
    private boolean existsInUser(String name) {
        try {
            java.io.File ud = StickerPanel.userDir(this);
            return ud != null && new java.io.File(ud, name).isFile();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 气泡旁的小头像，走缓存，不要每次解码。 */
    /** 气泡旁的小头像。mine=true 是你的，false 是她的。 */
    private View smallAvatar(boolean mine) {
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        g.setColor(Theme.dark ? 0xFF2A3340 : 0xFFDCEAF5);
        iv.setBackground(g);
        try {
            android.graphics.Bitmap b = mine
                    ? AvatarCache.getMine(this)
                    : AvatarCache.get(this);
            if (b != null) {
                // 关键：图片本身也要裁成圆形，不然方图会盖住圆背景
                iv.setImageDrawable(new CircleImageDrawable(b));
            } else if (mine) {
                iv.setImageDrawable(fallbackPersonIcon());
            }
        } catch (Throwable ignored) {}
        // 让图片跟着圆走
        iv.setClipToOutline(false);
        return iv;
    }

    /** 没设头像时的默认小图标（灰色人形）。 */
    private android.graphics.drawable.Drawable fallbackPersonIcon() {
        try {
            android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(
                    dp(40), dp(40), android.graphics.Bitmap.Config.ARGB_8888);
            android.graphics.Canvas cv = new android.graphics.Canvas(b);
            android.graphics.Paint p = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
            p.setColor(Theme.dark ? 0xFF6B7684 : 0xFFA9B4C0);
            float w = b.getWidth(), h = b.getHeight();
            cv.drawCircle(w / 2f, h * 0.36f, w * 0.19f, p);       // 头
            android.graphics.RectF body = new android.graphics.RectF(
                    w * 0.22f, h * 0.60f, w * 0.78f, h * 1.10f);
            cv.drawRoundRect(body, w * 0.28f, w * 0.28f, p);        // 身子
            return new android.graphics.drawable.BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return null;
        }
    }

    private Bubble addOneBubble(String text, boolean mine) {
        // 两种格式都要认：
        //   ${s001.png}          —— 现在指示模型用这个
        //   [[stickers/x.png]]   —— 老格式，历史记录里可能还有
        String asset = stickerNameIn(text);
        if (asset != null) {
            // 不做硬拦截，回不回同一张交给她自己判断（prompt 里有说明）
            if (mine) {
                lastUserSticker = asset;
            }
            return addImageBubble(asset, mine);
        }

        // 她写了表情标记、但文件真的找不到：
        // 不能把 ${委屈.gif} 这种原始标记显示出来，改成一句人话
        String t0 = text == null ? "" : text.trim();
        boolean looksLikeSticker = (t0.startsWith("${") && t0.endsWith("}"))
                || (t0.startsWith("[[") && t0.endsWith("]]"));
        if (looksLikeSticker) {
            String inner = t0.startsWith("${")
                    ? t0.substring(2, t0.length() - 1).trim()
                    : t0.substring(2, t0.length() - 2).trim();
            return addOneBubble("（翻了半天没找到那张「" + inner + "」）", mine);
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        // 头像和气泡顶部对齐 —— 底对齐会让单行气泡的头像浮在下面，看着别扭
        row.setGravity((mine ? Gravity.END : Gravity.START) | Gravity.TOP);
        row.setPadding(0, 0, dp(4), 0);

        // 她的消息左边挂头像
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        if (!mine) {
            row.addView(smallAvatar(false), avLp);
            View g = new View(this);
            row.addView(g, new LinearLayout.LayoutParams(dp(8), 1));
        }

        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16f);
        // 关键：她的气泡底色是 hersBubble()，文字必须配它，不能跟主题的 text()
        // 原来用 Theme.text()，深色主题下是浅色字，落在白气泡上就糊成一片
        tv.setTextColor(mine ? Theme.mineText() : Theme.theirsText());
        // iMessage 气泡内边距：左右 14，上下 9
        tv.setPadding(dp(14), dp(9), dp(14), dp(9));
        tv.setTextIsSelectable(true);
        tv.setBackground(mine ? Theme.bubbleMine() : Theme.bubbleTheirs());
        tv.setLineSpacing(dp(1), 1.18f);

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.leftMargin = 0;
        tlp.rightMargin = 0;
        tlp.topMargin = 0;
        tlp.bottomMargin = 0;
        // 气泡最宽占屏 72%
        try {
            int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.72f);
            tv.setMaxWidth(maxW);
        } catch (Throwable ignored) {}
        row.addView(tv, tlp);

        if (mine) {
            View g = new View(this);
            row.addView(g, new LinearLayout.LayoutParams(dp(8), 1));
            LinearLayout.LayoutParams avLp2 = new LinearLayout.LayoutParams(dp(32), dp(32));
            row.addView(smallAvatar(true), avLp2);
        }

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // iMessage 连发时气泡挨得近
        rlp.topMargin = dp(8);
        msgBox.addView(row, rlp);

        // 新气泡立刻套上自定义字体。
        // 之前只有 redraw() 末尾才 apply，所以刚发出去的消息是默认字体，
        // 退出再进来（重建气泡）才变回来。
        try {
            FontKit.r(this, tv);
        } catch (Throwable ignored) {}

        return new Bubble(tv);
    }

    /** 表情包那种图，不带气泡底，直接贴图 */
    private Bubble addImageBubble(String asset, boolean mine) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity((mine ? Gravity.END : Gravity.START) | Gravity.TOP);
        row.setPadding(0, 0, dp(4), 0);

        // 头像
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(32), dp(32));
        if (!mine) {
            row.addView(smallAvatar(false), avLp);
            View g = new View(this);
            row.addView(g, new LinearLayout.LayoutParams(dp(8), 1));
        }

        final GifView iv = new GifView(this);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int size = dp(130);
        row.addView(iv, new LinearLayout.LayoutParams(size, size));

        if (mine) {
            View g = new View(this);
            row.addView(g, new LinearLayout.LayoutParams(dp(8), 1));
            LinearLayout.LayoutParams avLp2 = new LinearLayout.LayoutParams(dp(32), dp(32));
            row.addView(smallAvatar(true), avLp2);
        }

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(8);
        msgBox.addView(row, rlp);

        final TextView dummy = new TextView(this);
        final String[] err = new String[]{null};

        new Thread(() -> {
            try {
                if (asset.startsWith("user/")) {
                    File f = new File(StickerPanel.userDir(this), asset.substring(5));
                    if (f.isFile()) {
                        iv.setSource(f);
                    }
                } else {
                    // assets 里的先落到临时文件，GIF 才能交给 Movie 播
                    File tmp = new File(getCacheDir(), "s_" + Math.abs(asset.hashCode()) + "_"
                            + Math.abs(asset.length()));
                    java.io.InputStream in = getAssets().open(asset);
                    FileOutputStream out = new FileOutputStream(tmp);
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                    in.close();
                    out.close();
                    iv.setSource(tmp);
                }
            } catch (Throwable e) {
                // assets 里的图落到临时文件可能失败，退到直接从 assets 解
                if (asset != null && !asset.startsWith("user/")) {
                    try {
                        java.io.InputStream in2 = getAssets().open(asset);
                        final android.graphics.Bitmap b2 =
                                android.graphics.BitmapFactory.decodeStream(in2);
                        in2.close();
                        if (b2 != null) {
                            ui.post(() -> iv.setImageBitmap(b2));
                            err[0] = null;
                        } else {
                            err[0] = "解码失败";
                        }
                    } catch (Throwable e2) {
                        err[0] = e2.getMessage();
                    }
                } else {
                    err[0] = e.getMessage();
                }
            }
            ui.post(() -> {
                if (err[0] != null) {
                    // 极端兜底：真解不出来也绝不留白，说一句人话
                    dummy.setText("（这张没加载出来）");
                    dummy.setTextColor(Theme.muted());
                    dummy.setTextSize(13);
                    row.removeView(iv);
                    row.addView(dummy);
                }
            });
        }).start();

        return new Bubble(dummy);
    }

    /**
     * 老版本存下的历史里可能混着 <状态>{...}</状态>，启动时洗一遍。
     * 洗过就不再重复做（存个标记）。
     */
    private void sanitizeHistoryOnce() {
        if (prefs().getBoolean("history_cleaned", false)) return;
        try {
            JSONArray h = chat.history();
            boolean changed = false;
            for (int i = 0; i < h.length(); i++) {
                JSONObject m = h.getJSONObject(i);
                if (!"assistant".equals(m.optString("role"))) continue;
                String raw = m.optString("content", "");
                String clean = Sanitize.clean(raw);
                if (!clean.equals(raw)) {
                    m.put("content", clean.isEmpty() ? "嗯" : clean);
                    changed = true;
                }
            }
            if (changed) chat.saveHistory();
            prefs().edit().putBoolean("history_cleaned", true).apply();
        } catch (Exception ignored) {}
    }

    private void scrollDown() {
        ui.post(() -> { try { scroll.fullScroll(View.FOCUS_DOWN); } catch (Exception ignored) {} });
    }

    private void doSend() {
        // 点一下立刻锁，防连点两次发出两条
        if (sending) return;
        sending = true;
        sendBtn.setAlpha(0.5f);

        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            sending = false;
            sendBtn.setAlpha(1f);
            return;
        }
        input.setText("");
        // 清空后立刻把焦点和键盘留在输入框上，不用再点一下
        try {
            input.requestFocus();
        } catch (Throwable ignored) {}
        doSend(text);
    }

    /**
     * 已经自己 append 过历史了，只想发请求、拿回复。
     * 给发表情用 —— 避免和 doSend 重复写一条 user 消息。
     */
    private void doSendOnly(String text) {
        if (text == null || text.trim().isEmpty()) {
            return;
        }
        final String t = text.trim();
        chat.saveHistory();
        autoBackup();
        scrollDown();

        streamingText.setLength(0);
        streamingBubble = addBubble("…", false);
        scrollDown();

        sending = true;
        sendBtn.setAlpha(0.5f);

        // 加完气泡再把焦点还给输入框（addBubble / scrollDown 可能改变焦点）
        keepFocus();

        JSONArray messages = chat.buildMessages(t);
        tokenIn = messages.toString().length() / 2;

        new Thread(() -> {
            ds.chat(apiKey(), messages, new DeepSeek.Callback() {
                public void onDelta(String piece) {
                    streamingText.append(piece);
                    final String now = Sanitize.cleanStreaming(streamingText.toString());
                    ui.post(() -> {
                        if (streamingBubble != null) {
                            streamingBubble.tv.setText(now.isEmpty() ? "…" : now);
                            scrollDown();
                        }
                    });
                }

                public void onError(String message) {
                    ui.post(() -> {
                        if (streamingBubble != null) {
                            streamingBubble.tv.setText("（出错了：" + message + "）");
                        }
                        sending = false;
                        sendBtn.setAlpha(1f);
                    });
                }

                public void onDone(String full) {
                    ui.post(() -> {
                        String t2 = Sanitize.clean(full == null ? "" : full);
                        try {
                            t2 = Sanitize.splitLines(t2);
                        } catch (Throwable ignored) {}
                        if (t2.isEmpty()) t2 = "嗯";
                        int idx = msgBox.indexOfChild((LinearLayout) streamingBubble.tv.getParent());
                        if (idx >= 0) msgBox.removeViewAt(idx);
                        addBubble(t2, false);
                        chat.append("assistant", t2, System.currentTimeMillis());
                        chat.saveHistory();
                        autoBackup();
                        tokenOut += t2.length() / 2;
                        sending = false;
                        sendBtn.setAlpha(1f);
                        streamingBubble = null;
                        refreshStatusLine();
                        scrollDown();
                    });
                }
            });
        }).start();
    }

    /** 真正发送（发送锁已在 doSend() 里拿到，这里不再抢） */
    private void doSend(String text) {
        if (text == null) { sending = false; sendBtn.setAlpha(1f); return; }
        text = text.trim();
        if (text.isEmpty()) { sending = false; sendBtn.setAlpha(1f); return; }
        if (apiKey().isEmpty()) {
            sending = false;
            sendBtn.setAlpha(1f);
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }

        if (msgBox.getChildCount() == 1) redraw();

        // 兜底：同一句话 600ms 内不许再进一次（防任何路径的双发）
        long now = System.currentTimeMillis();
        if (text.equals(lastSentText) && now - lastSentAt < 600) {
            sending = false;
            sendBtn.setAlpha(1f);
            return;
        }
        lastSentText = text;
        lastSentAt = now;

        addBubble(text, true);
        chat.append("user", text, System.currentTimeMillis());
        try {
            Session.touch(this, chat.sessionId(), text);
        } catch (Throwable ignored) {}
        // 主动消息已关闭
        chat.saveHistory();
        autoBackup();
        scrollDown();

        streamingText.setLength(0);
        streamingBubble = addBubble("…", false);
        scrollDown();

        sending = true;
        sendBtn.setAlpha(0.5f);

        JSONArray messages = chat.buildMessages(text);
        tokenIn = messages.toString().length() / 2;

        new Thread(() -> {
            ds.chat(apiKey(), messages, new DeepSeek.Callback() {
                public void onDelta(String piece) {
                    streamingText.append(piece);
                    // 流式也要过一遍过滤，不然会先闪出 <状态>{...}
                    final String now = Sanitize.cleanStreaming(streamingText.toString());
                    ui.post(() -> {
                        if (streamingBubble != null) {
                            streamingBubble.tv.setText(now.isEmpty() ? "…" : now);
                            scrollDown();
                        }
                    });
                }
                public void onError(String message) {
                    ui.post(() -> {
                        if (streamingBubble != null) streamingBubble.tv.setText("（出错了：" + message + "）");
                        sending = false;
                        sendBtn.setAlpha(1f);
                    });
                }
                public void onDone(String full) {
                    ui.post(() -> {
                        String t2 = Sanitize.clean(full == null ? "" : full);
                        if (t2.isEmpty()) t2 = "嗯";
                        // 收完才知道有几行，这时候按行拆成多个气泡
                        int idx = msgBox.indexOfChild((LinearLayout) streamingBubble.tv.getParent());
                        if (idx >= 0) msgBox.removeViewAt(idx);
                        // 长句先断开，存的和显示的用同一份
                        try {
                            t2 = Sanitize.splitLines(t2);
                        } catch (Throwable ignored) {}
                        addBubble(t2, false);
                        chat.append("assistant", t2, System.currentTimeMillis());
                        chat.saveHistory();
        autoBackup();
                        tokenOut += t2.length() / 2;
                        sending = false;
                        sendBtn.setAlpha(1f);
                        streamingBubble = null;
                        refreshStatusLine();
                        scrollDown();
                    });
                }
            });
        }).start();
    }






    // ================= 状态字段 =================
    private void showStatusFields() {
        final String[] keys = {"记忆条数", "Token 用量", "历史条数"};
        final String[] now = statusFieldState();
        MDialog.sheet(this, "状态栏显示什么", keys, i -> {
            boolean cur = "1".equals(now[i]);
            String[] arr = now.clone();
            arr[i] = cur ? "0" : "1";
            prefs().edit().putString("status_fields", join(arr)).apply();
            refreshStatusLine();
            toast(keys[i] + (cur ? " 隐藏" : " 显示"));
        }).show();
    }

    private String[] statusFieldState() {
        String raw = prefs().getString("status_fields", "1,0,1");
        String[] p = raw.split(",");
        String[] out = new String[3];
        for (int i = 0; i < 3; i++) out[i] = i < p.length ? p[i].trim() : "0";
        return out;
    }

    private String join(String[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) { if (i > 0) sb.append(','); sb.append(a[i]); }
        return sb.toString();
    }

    // ================= AI 分析偏好 =================




    /** 会话列表：切换 / 新建 / 选择删除。 */
    private void showSessions() {
        final android.app.Dialog[] holder = new android.app.Dialog[1];
        buildSessionsSheet(false, holder);
    }

    /**
     * 画会话 sheet。
     * @param selectMode true 时显示勾选框，顶部变成「删除已选」
     */
    private void buildSessionsSheet(final boolean selectMode,
                                    final android.app.Dialog[] holder) {
        try {
            final java.util.List<Session.Item> list = Session.list(this);
            final String cur = Session.current(this);

            final android.app.Dialog dlg = new android.app.Dialog(this,
                    android.R.style.Theme_DeviceDefault_NoActionBar);
            try {
                dlg.getWindow().setBackgroundDrawable(
                        new android.graphics.drawable.ColorDrawable(0x00000000));
                dlg.getWindow().setDimAmount(0.45f);
                dlg.getWindow().addFlags(
                        android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            } catch (Throwable ignored) {
            }
            holder[0] = dlg;

            final android.widget.FrameLayout root = new android.widget.FrameLayout(this);

            // 半透明遮罩，点外面关掉
            android.view.View mask = new android.view.View(this);
            mask.setBackgroundColor(0x66000000);
            mask.setOnClickListener(v -> dlg.dismiss());
            root.addView(mask, new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 底部面板
            android.widget.LinearLayout sheet = new android.widget.LinearLayout(this);
            sheet.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.graphics.drawable.GradientDrawable pb =
                    new android.graphics.drawable.GradientDrawable();
            pb.setCornerRadii(new float[]{
                    dp(24), dp(24), dp(24), dp(24), 0, 0, 0, 0});
            pb.setColor(Theme.dark ? 0xFF1E2229 : 0xFFFFFFFF);
            sheet.setBackground(pb);
            int pad = dp(16);
            sheet.setPadding(pad, dp(14), pad, dp(20));

            // 小横条
            android.view.View handle = new android.view.View(this);
            android.graphics.drawable.GradientDrawable hg =
                    new android.graphics.drawable.GradientDrawable();
            hg.setCornerRadius(dp(3));
            hg.setColor(Theme.dark ? 0x55FFFFFF : 0x33000000);
            handle.setBackground(hg);
            android.widget.LinearLayout.LayoutParams hp =
                    new android.widget.LinearLayout.LayoutParams(dp(34), dp(4));
            hp.gravity = Gravity.CENTER_HORIZONTAL;
            hp.bottomMargin = dp(14);
            sheet.addView(handle, hp);

            // 标题行
            android.widget.LinearLayout head =
                    new android.widget.LinearLayout(this);
            head.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.setPadding(0, 0, 0, dp(10));
            sheet.addView(head);

            android.widget.TextView title = new android.widget.TextView(this);
            title.setText(selectMode ? "勾选要删的聊天"
                    : "切换聊天  (\u957F\u6309\u6539\u6807\u9898)");
            title.setTextSize(17);
            title.setTextColor(Theme.text());
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            head.addView(title, new android.widget.LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            // 多选模式下才显示的「取消」
            final android.widget.TextView cancelSel = new android.widget.TextView(this);
            cancelSel.setText("取消");
            cancelSel.setTextSize(14);
            cancelSel.setTextColor(Theme.muted());
            cancelSel.setPadding(dp(10), dp(6), dp(10), dp(6));
            cancelSel.setClickable(true);
            cancelSel.setVisibility(android.view.View.GONE);
            head.addView(cancelSel);

            final android.widget.TextView pick = new android.widget.TextView(this);
            pick.setText("选择");
            pick.setTextSize(14);
            pick.setPadding(dp(10), dp(6), dp(4), dp(6));
            pick.setClickable(true);
            pick.setTextColor(Theme.dark ? 0xFF6FB8FF : 0xFF1A73E8);
            head.addView(pick);

            // 内容区
            final android.widget.LinearLayout bodyBox =
                    new android.widget.LinearLayout(this);
            bodyBox.setOrientation(android.widget.LinearLayout.VERTICAL);
            android.widget.ScrollView sc = new android.widget.ScrollView(this);
            sc.addView(bodyBox);
            sheet.addView(sc, new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

            final java.util.Set<String> selected = new java.util.HashSet<String>();
            final boolean[] sm0 = new boolean[]{selectMode};
            final Runnable upd = () -> {
                if (sm0[0]) {
                    int n = selected.size();
                    pick.setText(n == 0 ? "删除" : "删除(" + n + ")");
                    // 没选时置灰不可点
                    pick.setTextColor(n == 0
                            ? (Theme.dark ? 0x66FFFFFF : 0x44000000)
                            : 0xFFE05353);
                    pick.setEnabled(n > 0);
                    cancelSel.setVisibility(android.view.View.VISIBLE);
                } else {
                    pick.setText("选择");
                    pick.setTextColor(Theme.dark ? 0xFF6FB8FF : 0xFF1A73E8);
                    pick.setEnabled(true);
                    cancelSel.setVisibility(android.view.View.GONE);
                }
            };

            final boolean[] sm = sm0;
            final Runnable render = new Runnable() {
                @Override
                public void run() {
                    bodyBox.removeAllViews();
                    for (int i = 0; i < list.size(); i++) {
                        final Session.Item it = list.get(i);
                        final boolean here = it.id.equals(cur);
                        bodyBox.addView(sessionRow(dlg, it, here, sm[0], selected, () -> {
                            if (sm[0]) {
                                upd.run();
                            }
                        }));
                    }
                    // 新建（选择模式下不显示）
                    if (!sm[0]) {
                        bodyBox.addView(newChatRow(() -> {
                            try {
                                if (holder[0] != null) holder[0].dismiss();
                            } catch (Throwable ignored) {
                            }
                            Session.create(MainActivity.this, "新聊天");
                            ui.postDelayed(() -> {
                                switchToSession();
                                toast("开了一个新的");
                            }, 180);
                        }));
                    }
                    upd.run();
                    try {
                        FontKit.applyTo(MainActivity.this, bodyBox);
                    } catch (Throwable ignored) {
                    }
                }
            };

            pick.setOnClickListener(v -> {
                if (!sm[0]) {
                    sm[0] = true;
                    selected.clear();
                    title.setText("勾选要删的聊天");
                    render.run();
                } else {
                    if (selected.isEmpty()) {
                        return;
                    }
                    // 删
                    final int n = selected.size();
                    String tip = n == 1
                            ? "这个聊天的：\n"
                            + "· 对话历史\n"
                            + "· 聊天存档（历史聊天记录里也会一起消失）\n\n"
                            + "她的记忆不受影响。\n\n"
                            + "不能恢复。"
                            : "这就 " + n + " 个聊天的：\n"
                            + "· 对话历史\n"
                            + "· 聊天存档（历史聊天记录里也会一起消失）\n\n"
                            + "她的记忆不受影响。\n\n"
                            + "不能恢复。";
                    MDialog.card(MainActivity.this, "删除 " + n + " 个聊天？", tip,
                            "删除", () -> {
                                try {
                                    String last = null;
                                    for (String sid : selected) {
                                        last = Session.delete(MainActivity.this, sid);
                                    }
                                    if (last != null) {
                                        Session.setCurrent(MainActivity.this, last);
                                    }
                                    try {
                                        if (holder[0] != null) holder[0].dismiss();
                                    } catch (Throwable ignored) {
                                    }
                                    switchToSession();
                                    toast("删了 " + n + " 个");
                                } catch (Throwable t) {
                                    toast("删不掉：" + t);
                                }
                            }, "算了", null).show();
                }
            });

            cancelSel.setOnClickListener(v -> {
                sm0[0] = false;
                selected.clear();
                title.setText("切换聊天");
                render.run();
            });

            render.run();

            android.widget.FrameLayout.LayoutParams slp =
                    new android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.gravity = Gravity.BOTTOM;
            root.addView(sheet, slp);

            dlg.setContentView(root);
            dlg.setOnShowListener(x -> {
                try {
                    dlg.getWindow().setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                } catch (Throwable ignored) {
                }
            });
            dlg.show();

            try {
                FontKit.applyTo(this, root);
            } catch (Throwable ignored) {
            }
        } catch (Throwable t) {
            toast("打不开：" + t);
        }
    }

    /** 会话行。 */
    private android.view.View sessionRow(final android.app.Dialog dlg,
                                         final Session.Item it, final boolean here,
                                         final boolean selectMode,
                                         final java.util.Set<String> selected,
                                         final Runnable onToggle) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));

        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(14));

        final boolean checked = selectMode && selected.contains(it.id);

        final android.widget.TextView box;
        if (selectMode) {
            box = new android.widget.TextView(this);
            box.setTextSize(15);
            box.setGravity(Gravity.CENTER);
            box.setPadding(0, 0, dp(10), 0);
            box.setText(checked ? "\u2611" : "\u2610");
            box.setTextColor(checked
                    ? (Theme.dark ? 0xFF6FB8FF : 0xFF1A73E8) : Theme.muted());
            row.addView(box, new android.widget.LinearLayout.LayoutParams(
                    dp(26), ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            box = null;
        }

        android.widget.LinearLayout texts = new android.widget.LinearLayout(this);
        texts.setOrientation(android.widget.LinearLayout.VERTICAL);
        row.addView(texts, new android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.TextView t = new android.widget.TextView(this);
        t.setText((here ? "\u25CF " : "") + it.title);
        t.setTextSize(15.5f);
        t.setTextColor(here
                ? (Theme.dark ? 0xFF8FC4FF : 0xFF1A73E8) : Theme.text());
        if (here) {
            t.setTypeface(null, android.graphics.Typeface.BOLD);
        }
        texts.addView(t);

        android.widget.TextView sub = new android.widget.TextView(this);
        sub.setText(here ? "\u5F53\u524D\u5BF9\u8BDD  \u00B7  " + timeOf(it.at)
                : timeOf(it.at));
        sub.setTextSize(11.5f);
        sub.setTextColor(Theme.muted());
        sub.setPadding(0, dp(3), 0, 0);
        texts.addView(sub);

        g.setColor(here && !selectMode
                ? (Theme.dark ? 0xFF2A3A52 : 0xFFE8F1FB)
                : 0x00000000);
        row.setBackground(g);
        row.setClickable(true);

        // 长按改名
        row.setLongClickable(true);
        row.setOnLongClickListener(v -> {
            if (selectMode) {
                return false;
            }
            MDialog.input(MainActivity.this, "改标题",
                    "给这个聊天起个名字", it.title, text -> {
                        try {
                            String newTitle = text == null ? "" : text.trim();
                            if (newTitle.isEmpty()) {
                                return;
                            }
                            Session.rename(MainActivity.this, it.id, newTitle);
                            toast("改好了");
                            // 关掉重开，刷新标题
                            try {
                                if (dlg != null) {
                                    dlg.dismiss();
                                }
                            } catch (Throwable ignored) {
                            }
                            ui.postDelayed(() -> showSessions(), 180);
                        } catch (Throwable ignored) {
                        }
                    }).show();
            return true;
        });

        row.setOnClickListener(v -> {
            if (selectMode) {
                if (selected.contains(it.id)) {
                    selected.remove(it.id);
                    box.setText("\u2610");
                    box.setTextColor(Theme.muted());
                } else {
                    selected.add(it.id);
                    box.setText("\u2611");
                    box.setTextColor(Theme.dark ? 0xFF6FB8FF : 0xFF1A73E8);
                }
                if (onToggle != null) {
                    onToggle.run();
                }
            } else {
                if (it.id.equals(Session.current(this))) {
                    toast("已经在这个聊天里了");
                    return;
                }
                // 先关 sheet，再切会话（顺序很重要，否则界面不会跳）
                try {
                    if (dlg != null) {
                        dlg.dismiss();
                    }
                } catch (Throwable ignored) {
                }
                Session.setCurrent(this, it.id);
                final Session.Item target = it;
                // 等 sheet 收起来的动画走完再切
                ui.postDelayed(() -> {
                    switchToSession();
                    toast("切到「" + target.title + "」");
                }, 180);
            }
        });

        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        row.setLayoutParams(lp);
        return row;
    }

    /** 「＋ 新开一个聊天」那一行。 */
    private android.view.View newChatRow(final Runnable onClick) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(this);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(14), dp(12), dp(14));

        android.widget.TextView t = new android.widget.TextView(this);
        t.setText("\uFF0B  \u65B0\u5F00\u4E00\u4E2A\u804A\u5929");
        t.setTextSize(15.5f);
        t.setTextColor(Theme.dark ? 0xFF6FB8FF : 0xFF1A73E8);
        row.addView(t);

        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (onClick != null) {
                onClick.run();
            }
        });
        return row;
    }

    /** 切换会话后重建 Chat + 重画。 */
    private void switchToSession() {
        try {
            String sid = Session.current(this);
            ChatArchive.useSession(this, sid);
            chat = new Chat(this, sid);
            streamingBubble = null;
            streamingText.setLength(0);
            sending = false;
            try {
                if (sendBtn != null) {
                    sendBtn.setAlpha(1f);
                    sendBtn.setEnabled(true);
                }
            } catch (Throwable ignored) {
            }
            redraw();
            refreshStatusLine();
        } catch (Throwable ignored) {
        }
    }

    /** 顶栏那个 + 号：新开一个聊天。 */
    private void onNewSession() {
        try {
            if (chat.history().length() == 0) {
                toast("现在就是新聊天");
                return;
            }
            Session.create(this, "新聊天");
            switchToSession();
            toast("开了一个新的");
        } catch (Throwable t) {
            toast("出错：" + t);
        }
    }

    /**
     * 分析最近的对话，把关于他的偏好/习惯提炼出来，存进用户档案。
     * 手动触发，不是自动跑。
     */
    private void doAnalyze() {
        if (apiKey().isEmpty()) {
            toast("先填 Key");
            return;
        }
        JSONArray h = chat.history();
        if (h.length() < 6) {
            toast("先多聊几句再分析");
            return;
        }

        StringBuilder convo = new StringBuilder();
        int from = Math.max(0, h.length() - 40);
        for (int i = from; i < h.length(); i++) {
            try {
                JSONObject m = h.getJSONObject(i);
                convo.append("user".equals(m.optString("role")) ? "他：" : "她：")
                        .append(m.optString("content")).append('\n');
            } catch (Exception ignored) {
            }
        }

        toast("分析中…");
        JSONArray msgs = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", "你是记忆整理助手。读对话，提炼出关于「他」的偏好、习惯、忌讳，"
                    + "每条一行，不要编号不要解释，最多 3 条，没有就回空。");
            msgs.put(sys);
            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", convo.toString());
            msgs.put(u);
        } catch (Exception ignored) {
        }

        new Thread(() -> {
            final StringBuilder sb = new StringBuilder();
            ds.chat(apiKey(), msgs, new DeepSeek.Callback() {
                public void onDelta(String piece) {
                    sb.append(piece);
                }

                public void onError(String m) {
                    ui.post(() -> toast("分析失败：" + m));
                }

                public void onDone(String full) {
                    final String t = full == null ? "" : full.trim();
                    ui.post(() -> {
                        if (t.isEmpty() || t.equals("空")) {
                            toast("这次没分析出新东西");
                            return;
                        }
                        String[] lines = t.split("\n");
                        int n = 0;        // 真正写进去的
                        int dup = 0;      // 因为重复被跳过的
                        int tried = 0;    // 尝试的条数
                        for (String ln : lines) {
                            String c = ln.replaceAll("^[\\s\\-\u00B7\u2022\\d\\.\u3001]+", "").trim();
                            if (c.length() < 4) {
                                continue;
                            }
                            if (tried >= 3) {
                                break;
                            }
                            tried++;
                            try {
                                // addUser 返回 boolean：重复的会被拦下来
                                if (new Memory(MainActivity.this).addUser(c)) {
                                    n++;
                                } else {
                                    dup++;
                                }
                            } catch (Exception ignored) {
                            }
                        }

                        toast("存了 " + n + " 条");
                        refreshStatusLine();
                    });
                }
            });
        }, "analyze").start();
    }













    /** 时间显示：今天只显示时分，更早的带月日。 */
    private String timeOf(long at) {
        try {
            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.setTimeInMillis(at);
            boolean sameDay = now.get(java.util.Calendar.YEAR) == c.get(java.util.Calendar.YEAR)
                    && now.get(java.util.Calendar.DAY_OF_YEAR) == c.get(java.util.Calendar.DAY_OF_YEAR);
            String fmt = sameDay ? "HH:mm" : "MM-dd HH:mm";
            return new java.text.SimpleDateFormat(fmt, java.util.Locale.US)
                    .format(new java.util.Date(at));
        } catch (Throwable t) {
            return "";
        }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override
    public void onBackPressed() {
        if (bgPicker != null && bgPicker.isShown()) { bgPicker.hide(); return; }
        if (stickers != null && stickers.isShown()) {
            stickers.hide();
            ui.postDelayed(this::showKeyboard, 200);   // 收表情后把键盘叫回来
            return;
        }
        if (drawer != null && drawer.isShown()) {
            // 抽屉里有二级页先退一级
            if (drawer.isAtSubPage() && drawer.popPage()) return;
            drawer.hide();
            return;
        }
        super.onBackPressed();
    }
}
