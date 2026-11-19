package com.dafeiyu.app;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 设置抽屉（2026-11-13 晚加）
 * 从右侧滑出，装的是：她是谁、气泡、背景、连接、聊天、Token。
 */
public class SettingsDrawer {

    public interface Host {
        String getHost();
        String getPort();
        String getToken();
        String getHerName();
        void setHerName(String n);
        int getSkin();
        void setSkin(int idx);
        void onChangeAvatar();
        void onCover();
        void onSessions();     // 切换聊天
        void onExportChat();
        void onImport();
        void onMyAvatar();
        void onDescribeStickers();
        void onTokenStats();
        void onPersona();
        void onKeys();
        void onAiConfig();
        void onTheme();
        void onStatusToggle();
        void onStatusFields();
        void onMemories();
        void onCompress();
        void onAnalyze();
        void onChatBg();
        void onArchiveBg();
        boolean isStatusOn();
        void onClose();

        // ---- 新增入口 ----
        void onArchive();     // 聊天记录
        void onSearch();      // 搜索
        void onPersonaLib();  // 人格库
        void onGraph();       // 记忆图谱
        void onBalance();     // 余额
    }

    private final Activity act;
    private final Host host;
    private final LinearLayout root;
    private final LinearLayout body;
    private TextView titleView;              // 标题，二级页要改它
    private Runnable onBackOverride;          // 二级页的返回动作
    private boolean atSubPage = false;

    public SettingsDrawer(Activity a, Host h) {
        this.act = a;
        this.host = h;
        Theme.init(a);

        // 遮罩 + 抽屉，一起塞进一个 FrameLayout 由调用方 addView
        root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        // 半透明玻璃底，让背景透出来一点
        GradientDrawable shellBg = new GradientDrawable();
        // 液态玻璃底
        GradientDrawable gBody = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                Theme.dark ? new int[]{0xFF0B1B33, 0xFF081628}
                           : new int[]{0xFFF7FAFD, 0xFFEFF4F9});   // 右侧纯色区
        // 左边留圆角，右侧贴边
        gBody.setCornerRadius(dp(0));   // 整页式，不留圆角
        gBody.setStroke(dp(1), Theme.dark ? 0x33FFFFFF : 0xAAFFFFFF);

        GradientDrawable gSheen = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.dark ? 0x22FFFFFF : 0x55FFFFFF, 0x00FFFFFF});
        gSheen.setCornerRadius(dp(0));

        // 背景图 + 一层薄雾（暗色模式雾更重），这样玻璃卡片才透得出来
        android.graphics.drawable.Drawable bgImg;
        try {
            bgImg = act.getResources().getDrawable(R.drawable.bg_mist);
        } catch (Exception e) {
            bgImg = gBody;
        }

        // 背景图整张铺满（恢复原样），上面盖一层很淡的白纱保证文字读得清
        GradientDrawable veil = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                Theme.dark ? new int[]{0x66000000, 0x88000000}
                           : new int[]{0x40FFFFFF, 0x66FFFFFF});

        root.setBackground(new android.graphics.drawable.LayerDrawable(
                new android.graphics.drawable.Drawable[]{bgImg, veil}));
        if (android.os.Build.VERSION.SDK_INT >= 21) root.setElevation(dp(20));
        root.setVisibility(View.GONE);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 标题栏
        LinearLayout head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(18), dp(16), dp(12), dp(12));
        GradientDrawable headBg = new GradientDrawable();
        headBg.setColor(0x00000000);   // 顶栏也透明，图一那样飘在背景上
        head.setBackground(headBg);

        // 返回按钮：圆形浅底 + 画出来的箭头（不用字体，保证居中）
        View back = new android.view.View(a) {
            @Override
            protected void onDraw(android.graphics.Canvas cv) {
                super.onDraw(cv);
                float w = getWidth(), h = getHeight();
                float cx = w / 2f, cy = h / 2f;
                float r = Math.min(w, h) * 0.16f;
                android.graphics.Paint p = new android.graphics.Paint(
                        android.graphics.Paint.ANTI_ALIAS_FLAG);
                p.setStyle(android.graphics.Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(2f, h * 0.075f));
                p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
                p.setStrokeJoin(android.graphics.Paint.Join.ROUND);
                p.setColor(Theme.text());
                // 画一个 <
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(cx + r * 0.5f, cy - r);
                path.lineTo(cx - r * 0.5f, cy);
                path.lineTo(cx + r * 0.5f, cy + r);
                cv.drawPath(path, p);
            }
        };
        GradientDrawable backBg = new GradientDrawable();
        backBg.setShape(GradientDrawable.OVAL);
        backBg.setColor(Theme.dark ? 0x22FFFFFF : 0xFFEDF1F6);
        back.setBackground(backBg);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(36), dp(36));
        blp.rightMargin = dp(12);
        head.addView(back, blp);
        back.setOnClickListener(v -> {
            if (!popPage()) hide();      // 有上一级就回，没有才关
        });

        TextView title = new TextView(a);
        titleView = title;
        title.setText("更多");
        title.setTextSize(17);
        title.setTextColor(Theme.text());
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD,
                android.graphics.Typeface.BOLD);
        title.getPaint().setFakeBoldText(true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        head.addView(title, tp);

        // 右边留个等宽的占位，标题才真居中
        // 注意：左边返回按钮有 12dp 右边距，这里也要补 12dp 左边距，否则差 6dp
        android.view.View spacer = new android.view.View(a);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(dp(36), dp(36));
        slp.leftMargin = dp(12);
        head.addView(spacer, slp);

        root.addView(head);

        // 滚动体
        ScrollView sc = new ScrollView(a);
        body = new LinearLayout(a);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(10), dp(10), dp(10), dp(30));
        sc.addView(body);
        root.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    /** 现在是不是在二级页 */
    public boolean isAtSubPage() { return atSubPage; }

    /** 从二级页回主设置。返回 true = 确实回了一层 */
    public boolean popPage() {
        if (!atSubPage) return false;
        atSubPage = false;
        titleView.setText("更多");
        build();
        return true;
    }

    /** 进二级页：标题换成 title，内容由 builder 自己往 body 里塞 */
    public void pushPage(String title, Runnable builder) {
        atSubPage = true;
        titleView.setText(title);
        body.removeAllViews();
        builder.run();
        if (body.getChildCount() == 0) {
            // 什么都没加，给个占位
            TextView tv = new TextView(act);
            tv.setText("这个页面还在做");
            tv.setTextColor(Theme.muted());
            tv.setPadding(dp(14), dp(40), dp(14), dp(40));
            body.addView(tv);
        }
    }

    /** 供外部（MainActivity）往二级页里加东西用 */
    public LinearLayout pageBody() { return body; }
    public Activity activity() { return act; }
    public int dpv(int v) { return dp(v); }

    public View getView() { return root; }
    public boolean isShown() { return root.getVisibility() == View.VISIBLE; }

    public void show() {
        build();
        root.setVisibility(View.VISIBLE);
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(180).start();
    }

    public void hide() {
        root.animate().alpha(0f).setDuration(150).withEndAction(() -> {
            root.setVisibility(View.GONE);
            if (host != null) host.onClose();
        }).start();
    }

    // ---------- 构建 ----------
    private void build() {
        body.removeAllViews();

        section("她是谁");
        itemCard("✏️", "改名字", "她现在叫 " + host.getHerName(), () -> {
            MDialog.input(act, "她叫什么？", "输入名字", host.getHerName(), s -> {
                if (s != null && !s.isEmpty()) { host.setHerName(s); build(); }
            }).show();
        });
        itemCard("🖼️", "换头像", "挑一张图当她的脸", () -> host.onChangeAvatar());
        itemCard("὆4".replace("὆4", "👤"), "换我的头像", "你自己的头像", () -> host.onMyAvatar());
        itemCard("🔍", "认识表情包", "让她看一眼每张图是什么", () -> host.onDescribeStickers());
        itemCard("🌄", "换封面", "聊天背景图，也当 App 封面", () -> host.onCover());
        itemCard("🎭", "换人格", "人格是提示词模板", () -> host.onPersona());
        itemCard("📚", "人格库", "存好几份，随时切换", () -> host.onPersonaLib());

        // 气泡配色（横向色块，保持原样）
        section("气泡皮肤");
        LinearLayout skins = new LinearLayout(act);
        skins.setOrientation(LinearLayout.HORIZONTAL);
        skins.setPadding(dp(6), 0, dp(6), dp(10));
        int cur = host.getSkin();
        for (int i = 0; i < Theme.SKIN_NAMES.length; i++) {
            final int idx = i;
            TextView tc = new TextView(act);
            tc.setText(Theme.SKIN_NAMES[i]);
            tc.setTextSize(12);
            tc.setGravity(Gravity.CENTER);
            tc.setPadding(0, dp(10), 0, dp(10));
            int[] c = Theme.SKIN_COLORS[i];
            // 色块上的字用这个皮肤配套的深色文字，白底/奶油底才看得清
            tc.setTextColor(c[2]);
            GradientDrawable g = new GradientDrawable(
                    GradientDrawable.Orientation.TL_BR, new int[]{c[0], c[1]});
            g.setCornerRadius(dp(13));
            if (idx == cur) g.setStroke(dp(2), Theme.accent());
            tc.setBackground(g);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.rightMargin = dp(4);
            tc.setLayoutParams(lp);
            tc.setOnClickListener(v -> { host.setSkin(idx); build(); });
            skins.addView(tc);
        }
        body.addView(skins);

        section("主题与外观");
        itemCard("🎨", "主题切换", Theme.dark ? "当前：深色" : "当前：浅色", () -> host.onTheme());
        itemCard("🖼️", "此对话背景设置", "背景色 / 深浅", () -> host.onChatBg());
        itemCard("🖼", "历史记录背景", "聊天记录列表的背景图", () -> host.onArchiveBg());

        // 状态栏开关
        itemSwitchCard("📈", "状态栏", "聊天顶上那一行显不显示",
                host.isStatusOn(), on -> host.onStatusToggle());

        section("AI 设置");
        itemCard("📊", "AI 状态栏设置", "定义状态字段", () -> host.onStatusFields());
        itemCard("🔑", "DeepSeek API Key", mask(host.getToken()), () -> host.onAiConfig());

        section("记忆");
        itemCard("🕸", "记忆图谱", "看看她的记忆怎么连起来的", () -> host.onGraph());
        itemCard("🧠", "共享记忆与偏好", "看看她记住了什么", () -> host.onMemories());
        itemCard("🗜", "压缩上下文", "把早期对话压成摘要，省 token 又不失忆", () -> host.onCompress());
        itemCard("✨", "AI 分析并提取偏好", "手动触发，存到用户档案", () -> host.onAnalyze());

        section("此对话设置");
        itemCard("📜", "历史聊天记录", "所有聊天的全部记录", () -> host.onArchive());
        itemCard("🔍", "搜索聊天记录", "窗口和历史一起搜", () -> host.onSearch());
        itemCard("🗂️", "切换聊天", "每个聊天独立记录，互不干扰", () -> host.onSessions());
        itemCard("📤", "导出全部数据", "聊天记录 + 记忆 + 设置", () -> host.onExportChat());
        itemCard("📥", "导入数据", "从备份恢复聊天记录和记忆", () -> host.onImport());
        itemCard("📈", "Token 统计", "本轮 / 累计 / 上下文", () -> host.onTokenStats());
        itemCard("¥", "余额", "看一眼 DeepSeek 账户还剩多少", () -> host.onBalance());

        section("接口");
        itemCard("📡", "运行方式", "独立直连 · 不经过电脑", () -> {
            MDialog.card(act, "运行方式",
                    "这个版本把后端直接装在手机里，自己去调 DeepSeek。\n\n"
                            + "不需要电脑、不需要同一个 WiFi、不需要网桥。\n"
                            + "只有 API Key 存在本机。",
                    "知道了", null, null, null).show();
        });

        // 抽屉是动态创建的，onCreate 那次 FontKit.apply 覆盖不到，这里补一次
        try {
            FontKit.applyTo(act, root);
        } catch (Throwable ignored) {}
    }

    private String mask(String t) {
        if (t == null || t.isEmpty()) return "没设";
        return t.substring(0, Math.min(6, t.length())) + "…";
    }

    private void section(String title) {
        TextView h = new TextView(act);
        h.setText(title);
        h.setTextSize(12);              // 11sp 太小
        h.setTextColor(Theme.mutedOnGlass());
        h.setPadding(dp(12), dp(16), dp(12), dp(6));
        h.setLetterSpacing(0.06f);
        body.addView(h);
    }

    /** 带开关的那行：右边是绿/灰胶囊，点完当场翻 */
    private void itemSwitch(String ico, String label, boolean on, java.util.function.Consumer<Boolean> onToggle) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(13), dp(14), dp(13));
        LiquidGlass.glassPanel(row, 20, Theme.dark, false);

        TextView i = new TextView(act);
        i.setText(ico);
        i.setTextSize(16);
        i.setGravity(Gravity.CENTER);
        GradientDrawable ig = new GradientDrawable();
        ig.setCornerRadius(dp(11));
        ig.setColor(Theme.dark ? 0x3347A8FF : 0x1A4A9EFF);
        i.setBackground(ig);
        i.setTextColor(Theme.dark ? 0xFF6FB6FF : 0xFF2E7BEA);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(36), dp(36));
        ip.rightMargin = dp(12);
        row.addView(i, ip);

        TextView l = new TextView(act);
        l.setText(label);
        l.setTextSize(14.5f);
        l.setTextColor(Theme.textOnGlass());
        row.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 右边的开关（画成胶囊）
        final TextView sw = new TextView(act);
        final boolean[] state = {on};
        paintSwitch(sw, state[0]);
        row.addView(sw);

        row.setOnClickListener(v -> {
            state[0] = !state[0];
            paintSwitch(sw, state[0]);          // ① 当场翻
            if (onToggle != null) onToggle.accept(state[0]);   // ② 通知外面
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        body.addView(row, lp);
    }

    /** 画一个开关：开=绿，关=灰 */
    private void paintSwitch(TextView sw, boolean on) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(11));
        g.setColor(on ? 0xFF4CD07A : (Theme.dark ? 0x33FFFFFF : 0xFFD5DAE0));
        sw.setBackground(g);
        sw.setText(on ? "  开  " : "  关  ");
        sw.setTextSize(11.5f);
        sw.setTextColor(on ? 0xFFFFFFFF : Theme.muted());
        sw.setGravity(Gravity.CENTER);
        sw.setPadding(dp(6), dp(4), dp(6), dp(4));
        sw.setMinWidth(dp(52));
    }

    /** 截图那种卡片：左边图标、中间主标题+副标题、右边 > */
    /** 带副标题的开关卡片 */
    private void itemSwitchCard(String ico, String title, String sub,
                                boolean on, java.util.function.Consumer<Boolean> onToggle) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(16), dp(15));

        GradientDrawable gBody = new GradientDrawable();
        gBody.setCornerRadius(dp(26));
        gBody.setColor(Theme.dark ? 0x3DFFFFFF : 0x99FFFFFF);
        gBody.setStroke(dp(1), Theme.dark ? 0x66FFFFFF : 0xE6FFFFFF);
        row.setBackground(gBody);
        if (android.os.Build.VERSION.SDK_INT >= 21) row.setElevation(0);

        // 同样的细线图标
        android.widget.ImageView i = new android.widget.ImageView(act);
        i.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        i.setBackgroundColor(0x00000000);
        i.setImageDrawable(new LineIconDrawable(
                iconKindFor(ico, title),
                Theme.dark ? 0xFF7FC4FF : 0xFF2E7BEA));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24));
        ip.rightMargin = dp(16);
        row.addView(i, ip);

        LinearLayout mid = new LinearLayout(act);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView tt = new TextView(act);
        tt.setText(title);
        tt.setTextSize(16f);
        tt.setTextColor(Theme.textOnGlass());
        tt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD);
        tt.getPaint().setFakeBoldText(true);
        mid.addView(tt);
        if (sub != null && !sub.isEmpty()) {
            TextView st = new TextView(act);
            st.setText(sub);
            st.setTextSize(13.5f);          // 12.5 太小，玻璃底上看不清
            st.setTextColor(Theme.mutedOnGlass());
            st.setPadding(0, dp(4), 0, 0);
            mid.addView(st);
        }
        row.addView(mid, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView sw = new TextView(act);
        final boolean[] state = {on};
        paintSwitch(sw, state[0]);
        LinearLayout.LayoutParams sp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp2.leftMargin = dp(8);
        row.addView(sw, sp2);

        row.setOnClickListener(v -> {
            state[0] = !state[0];
            paintSwitch(sw, state[0]);
            if (onToggle != null) onToggle.accept(state[0]);
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        body.addView(row, lp);
    }

    /** 每个设置项 → 图二那种线性图标 */
    private int iconKindFor(String key, String title) {
        String t = title == null ? "" : title;
        if (t.contains("名字")) return LineIconDrawable.PEN;
        if (t.contains("头像")) return LineIconDrawable.PHOTO;
        if (t.contains("封面") || t.contains("背景")) return LineIconDrawable.PHOTO;
        if (t.contains("人格")) return LineIconDrawable.LAYER;
        if (t.contains("主题")) return LineIconDrawable.STAR;
        if (t.contains("状态栏")) return LineIconDrawable.WAVE;
        if (t.contains("Token")) return LineIconDrawable.TARGET;
        if (t.contains("音乐")) return LineIconDrawable.MUSIC;
        if (t.contains("用户")) return LineIconDrawable.PERSON;
        if (t.contains("皮肤")) return LineIconDrawable.BLOCKS;
        if (t.contains("定时")) return LineIconDrawable.CLOCK;
        if (t.contains("音效")) return LineIconDrawable.KEYBOARD;
        if (t.contains("记忆") || t.contains("偏好")) return LineIconDrawable.LAYER;
        if (t.contains("分析") || t.contains("提取")) return LineIconDrawable.STAR;
        if (t.contains("导出")) return LineIconDrawable.DOWNLOAD;
        if (t.contains("清空")) return LineIconDrawable.BROOM;
        if (t.contains("压缩") || t.contains("上下文")) return LineIconDrawable.LAYER;
        if (t.contains("配置") || t.contains("AI 信息")) return LineIconDrawable.SLIDERS;
        if (t.contains("API") || t.contains("Key")) return LineIconDrawable.KEY;
        if (t.contains("运行")) return LineIconDrawable.SIGNAL;
        if (t.contains("统计")) return LineIconDrawable.CHART;
        return LineIconDrawable.LAYER;
    }

    private void itemCard(String ico, String title, String sub, Runnable onClick) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(16), dp(15));

        // 毛玻璃卡片（照着图一那种：很透、细白描边、大圆角）
        GradientDrawable gBody = new GradientDrawable();
        gBody.setCornerRadius(dp(26));
        gBody.setColor(Theme.dark ? 0x3DFFFFFF : 0x99FFFFFF);   // 半透明白磨砂，透出背景
        gBody.setStroke(dp(1), Theme.dark ? 0x66FFFFFF : 0xE6FFFFFF);
        row.setBackground(gBody);
        if (android.os.Build.VERSION.SDK_INT >= 21) row.setElevation(0);   // 不要阴影，飘起来就不像玻璃了

        // 卡片之间的间距放宽
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.bottomMargin = dp(12);
        row.setLayoutParams(rlp);

        // 图标（单色线性，图二那种简约风）—— ImageView + Drawable，不用自定义 View
        android.widget.ImageView i = new android.widget.ImageView(act);
        i.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        i.setBackgroundColor(0x00000000);          // 强制透明底
        i.setImageDrawable(new LineIconDrawable(
                iconKindFor(ico, title),
                Theme.dark ? 0xFF7FC4FF : 0xFF2E7BEA));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24));
        ip.rightMargin = dp(16);
        row.addView(i, ip);

        // 中间：主标题 + 副标题
        LinearLayout mid = new LinearLayout(act);
        mid.setOrientation(LinearLayout.VERTICAL);
        TextView tt = new TextView(act);
        tt.setText(title);
        tt.setTextSize(16f);
        tt.setTextColor(Theme.textOnGlass());
        tt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD);
        tt.getPaint().setFakeBoldText(true);
        mid.addView(tt);
        if (sub != null && !sub.isEmpty()) {
            TextView st = new TextView(act);
            st.setText(sub);
            st.setTextSize(12.5f);
            st.setTextColor(Theme.mutedOnGlass());
            st.setPadding(0, dp(3), 0, 0);
            mid.addView(st);
        }
        row.addView(mid, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // 右边 ›（细箭头，图二那种）
        android.widget.ImageView ar = new android.widget.ImageView(act);
        ar.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        ar.setBackgroundColor(0x00000000);
        ar.setImageDrawable(new LineIconDrawable(
                LineIconDrawable.CHEVRON,
                Theme.dark ? 0x8068A8DE : 0x592D3B4A));
        row.addView(ar, new LinearLayout.LayoutParams(dp(18), dp(18)));

        row.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        body.addView(row, lp);
    }

    private void item(String ico, String label, String val, Runnable onClick) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(13), dp(14), dp(13));

        // 跟 itemCard 完全一样的卡片样式
        GradientDrawable gBody = new GradientDrawable();
        gBody.setCornerRadius(dp(26));
        gBody.setColor(Theme.dark ? 0x3DFFFFFF : 0x99FFFFFF);
        gBody.setStroke(dp(1), Theme.dark ? 0x66FFFFFF : 0xE6FFFFFF);
        row.setBackground(gBody);
        if (android.os.Build.VERSION.SDK_INT >= 21) row.setElevation(0);
        row.setPadding(dp(16), dp(15), dp(16), dp(15));

        // 同样的细线图标（不要 emoji、不要蓝底块）
        android.widget.ImageView i = new android.widget.ImageView(act);
        i.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        i.setBackgroundColor(0x00000000);
        i.setImageDrawable(new LineIconDrawable(
                iconKindFor(ico, label),
                Theme.dark ? 0xFF7FC4FF : 0xFF2E7BEA));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(24), dp(24));
        ip.rightMargin = dp(16);
        row.addView(i, ip);

        TextView l = new TextView(act);
        l.setText(label);
        l.setTextSize(16f);
        l.setTextColor(Theme.textOnGlass());
        l.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD);
        l.getPaint().setFakeBoldText(true);
        row.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (val != null && !val.isEmpty()) {
            TextView v = new TextView(act);
            v.setText(val);
            v.setTextSize(12);
            v.setTextColor(Theme.mutedOnGlass());
            v.setMaxWidth(dp(140));
            v.setSingleLine(true);
            v.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(v);
        }

        row.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        body.addView(row, lp);
    }

    private int dp(int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density);
    }
}
