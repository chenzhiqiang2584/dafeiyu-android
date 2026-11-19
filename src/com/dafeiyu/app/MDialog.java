package com.dafeiyu.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 液态玻璃风格弹窗（2026-11-13 v2）
 *
 * 参考的是半透明玻璃质感：
 *   · 每行是一块独立圆角卡片（不连成一片）
 *   · 半透明白 + 冷色细描边
 *   · 左侧带蓝色圆角小图标
 *   · 悬浮阴影
 *
 * 注：安卓原生画不了真毛玻璃（要实时模糊背后），
 * 这里用半透明 + 描边 + 阴影做近似。
 */
public class MDialog {

    public interface OnPick { void onPick(int index); }
    public interface OnInput { void onInput(String text); }

    // ---------- 玻璃色板 ----------
    private static int glassBg()    { return Theme.dark ? 0xE61F242B : 0xF2FFFFFF; }
    private static int glassEdge()  { return Theme.dark ? 0x1FFFFFFF : 0x66D8DEE6; }
    private static int iconBg()     { return Theme.dark ? 0x3347A8FF : 0x1A4A9EFF; }
    private static int iconFg()     { return Theme.dark ? 0xFF6FB6FF : 0xFF2E7BEA; }
    private static int subText()    { return Theme.dark ? 0xFF8A939E : 0xFF8E959E; }

    /**
     * 玻璃行：圆角卡片，左边一个图标，右边文字
     * 参考图里那种一行一行的
     */
    static LinearLayout glassRow(Activity act, String icon, String title, String subtitle,
                                 boolean danger, Runnable onClick) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(act, 12), dp(act, 13), dp(act, 14), dp(act, 13));

        // 液态玻璃底
        LiquidGlass.glassPanel(row, 20, Theme.dark, false);

        // 左侧图标块
        if (icon != null && !icon.isEmpty()) {
            TextView ic = new TextView(act);
            ic.setText(icon);
            ic.setTextSize(17);
            ic.setGravity(Gravity.CENTER);
            GradientDrawable ig = new GradientDrawable();
            ig.setCornerRadius(dp(act, 12));
            ig.setColor(iconBg());
            ic.setBackground(ig);
            ic.setTextColor(iconFg());
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(act, 38), dp(act, 38));
            ip.rightMargin = dp(act, 13);
            row.addView(ic, ip);
        }

        // 文字区
        LinearLayout texts = new LinearLayout(act);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView t = new TextView(act);
        t.setText(title);
        t.setTextSize(15.5f);
        t.setTextColor(danger ? 0xFFE05B5B : Theme.text());
        texts.addView(t);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView s = new TextView(act);
            s.setText(subtitle);
            s.setTextSize(12);
            s.setTextColor(subText());
            s.setPadding(0, dp(act, 2), 0, 0);
            texts.addView(s);
        }

        row.addView(texts, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (onClick != null) {
            row.setOnClickListener(v -> onClick.run());
        }
        return row;
    }

    // ---------- 底部玻璃面板 ----------
    /**
     * 从底部滑出的玻璃面板
     * @param items  选项文字
     * @param icons  图标（可为 null）
     * @param subs   副标题（可为 null）
     */
    public static Dialog sheet(Activity act, String title,
                               String[] items, String[] icons, String[] subs,
                               OnPick cb) {
        Dialog d = new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        // 底部留白 = 24dp + 系统导航栏高度
        int navBar = 0;
        try {
            int id = act.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            if (id > 0) navBar = act.getResources().getDimensionPixelSize(id);
        } catch (Exception ignored) {}
        root.setPadding(dp(act, 12), dp(act, 10), dp(act, 12), navBar + dp(act, 16));

        // 面板底：半透明玻璃
        // 面板：液态玻璃（下沿是直角，贴着屏幕底）
        GradientDrawable pb = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                Theme.dark ? new int[]{0xF02A2F37, 0xF01E2229}
                           : new int[]{0xF5FFFFFF, 0xF5F2F5F9});
        pb.setCornerRadii(new float[]{
                dp(act, 30), dp(act, 30), dp(act, 30), dp(act, 30), 0, 0, 0, 0});
        pb.setStroke(dp(act, 1), Theme.dark ? 0x33FFFFFF : 0x99FFFFFF);

        GradientDrawable pSheen = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.dark ? 0x22FFFFFF : 0x44FFFFFF, 0x00FFFFFF});
        pSheen.setCornerRadii(new float[]{
                dp(act, 30), dp(act, 30), dp(act, 30), dp(act, 30), 0, 0, 0, 0});

        LayerDrawable pLayers = new LayerDrawable(new Drawable[]{
                pb, pSheen});
        root.setBackground(pLayers);
        if (android.os.Build.VERSION.SDK_INT >= 21) root.setElevation(dp(act, 16));

        // 顶部小横条
        View handle = new View(act);
        GradientDrawable hg = new GradientDrawable();
        hg.setCornerRadius(dp(act, 3));
        hg.setColor(Theme.dark ? 0x55FFFFFF : 0x55000000);
        handle.setBackground(hg);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(dp(act, 34), dp(act, 4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.topMargin = dp(act, 4);
        hp.bottomMargin = dp(act, 12);
        root.addView(handle, hp);

        if (title != null && !title.isEmpty()) {
            TextView t = new TextView(act);
            t.setText(title);
            t.setTextSize(16);
            t.setTextColor(Theme.text());
            t.setTypeface(null, android.graphics.Typeface.BOLD);
            t.setPadding(dp(act, 8), dp(act, 2), dp(act, 8), dp(act, 12));
            root.addView(t);
        }

        // 内容最多两屏，超了可以滚
        ScrollView sc = new ScrollView(act);
        sc.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            String ic = (icons != null && i < icons.length) ? icons[i] : null;
            String sb = (subs != null && i < subs.length) ? subs[i] : null;

            LinearLayout row = glassRow(act, ic, items[i], sb, false, () -> {
                d.dismiss();
                if (cb != null) cb.onPick(idx);
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(act, 8);
            list.addView(row, lp);
        }
        sc.addView(list);

        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        root.addView(sc, sp);
        // 兜底：底部多垫一块同色，绝对不漏黑
        View filler = new View(act);
        GradientDrawable fg = new GradientDrawable();
        fg.setColor(Theme.dark ? 0xF01A1F25 : 0xF7F2F5F9);
        filler.setBackground(fg);
        root.addView(filler, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 8)));

        d.setContentView(root);

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.32f;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.BOTTOM;
            w.setAttributes(lp);

            // 从底部顶到屏幕最下面，不留黑缝
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);

            if (android.os.Build.VERSION.SDK_INT >= 21) {
                w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
                w.addFlags(android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                // 导航栏透明，内容顶下去
                w.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            }
            // 让内容延伸到系统栏下面
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                w.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }
        }
        return d;
    }

    /** 简化版：只有文字选项 */
    public static Dialog sheet(Activity act, String title, String[] items, OnPick cb) {
        return sheet(act, title, items, null, null, cb);
    }

    // ---------- 玻璃卡片 ----------
    /** 卡片弹窗，内容放任意 View（2026-11-14 加） */
    public static Dialog cardView(Activity act, String title, View content, String positive) {
        Dialog d = new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 22), dp(act, 22), dp(act, 22), dp(act, 16));
        LiquidGlass.glassPanel(root, 28, Theme.dark, true);

        TextView t1 = new TextView(act);
        t1.setText(title);
        t1.setTextSize(16);
        t1.setTypeface(null, android.graphics.Typeface.BOLD);
        t1.setTextColor(Theme.text());
        t1.setPadding(0, 0, 0, dp(act, 12));
        root.addView(t1);

        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView ok = pill(act, positive == null ? "关闭" : positive, true);
        ok.setOnClickListener(v -> d.dismiss());
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(act, 46));
        op.topMargin = dp(act, 14);
        root.addView(ok, op);

        d.setContentView(root);

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.32f;
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            lp.gravity = Gravity.CENTER;
            w.setAttributes(lp);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (android.os.Build.VERSION.SDK_INT >= 21) {
                w.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            }
        }
        return d;
    }

    public static Dialog card(Activity act, String title, String message,
                              String positive, Runnable onPositive,
                              String negative, Runnable onNegative) {
        return card(act, title, message, null, positive, onPositive, negative, onNegative);
    }

    /** 带自定义内容区的版本：box 会插在提示文字和按钮之间 */
    public static Dialog card(Activity act, String title, String message, View content,
                              String positive, Runnable onPositive,
                              String negative, Runnable onNegative) {
        Dialog d = new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 22), dp(act, 22), dp(act, 22), dp(act, 16));

        LiquidGlass.glassPanel(root, 28, Theme.dark, true);

        if (title != null) {
            TextView t = new TextView(act);
            t.setText(title);
            t.setTextSize(17.5f);
            t.setTextColor(Theme.text());
            t.setTypeface(null, android.graphics.Typeface.BOLD);
            root.addView(t);
        }

        if (message != null) {
            ScrollView sc = new ScrollView(act);
            TextView m = new TextView(act);
            m.setText(message);
            m.setTextSize(14);
            m.setTextColor(subText());
            m.setLineSpacing(dp(act, 4), 1f);
            m.setPadding(0, dp(act, 12), 0, dp(act, 6));
            m.setTextIsSelectable(true);
            sc.addView(m);
            LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            root.addView(sc, sp);
        }

        if (content != null) {
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cp.topMargin = dp(act, 10);
            root.addView(content, cp);
        }

        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);
        btns.setPadding(0, dp(act, 12), 0, 0);

        if (negative != null) {
            TextView n = pill(act, negative, false);
            n.setOnClickListener(v -> { d.dismiss(); if (onNegative != null) onNegative.run(); });
            btns.addView(n);
        }
        if (positive != null) {
            TextView p = pill(act, positive, true);
            LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pp.leftMargin = dp(act, 8);
            p.setLayoutParams(pp);
            p.setOnClickListener(v -> { d.dismiss(); if (onPositive != null) onPositive.run(); });
            btns.addView(p);
        }
        root.addView(btns);

        d.setContentView(root);

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            // 窗口本身也清干净，免得露黑
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams dl = w.getAttributes();
            dl.dimAmount = 0.32f;
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = dp(act, 344);
            w.setAttributes(lp);
        }
        return d;
    }

    // ---------- 玻璃输入框 ----------
    public static Dialog input(Activity act, String title, String hint, String preset, OnInput cb) {
        Dialog d = new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 22), dp(act, 22), dp(act, 22), dp(act, 16));

        LiquidGlass.glassPanel(root, 28, Theme.dark, true);

        TextView t = new TextView(act);
        t.setText(title);
        t.setTextSize(17.5f);
        t.setTextColor(Theme.text());
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(t);

        final android.widget.EditText et = new android.widget.EditText(act);
        et.setText(preset == null ? "" : preset);
        et.setHint(hint == null ? "" : hint);
        et.setHintTextColor(subText());
        et.setTextSize(15.5f);
        et.setTextColor(Theme.text());
        et.setSingleLine(true);
        et.setPadding(dp(act, 16), dp(act, 13), dp(act, 16), dp(act, 13));

        GradientDrawable eb = new GradientDrawable();
        eb.setCornerRadius(dp(act, 15));
        eb.setColor(Theme.dark ? 0x33FFFFFF : 0xFFF4F6F9);
        eb.setStroke(dp(act, 1), glassEdge());
        et.setBackground(eb);

        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = dp(act, 16);
        root.addView(et, ep);

        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);

        TextView n = pill(act, "取消", false);
        n.setOnClickListener(v -> d.dismiss());
        btns.addView(n);

        TextView p = pill(act, "确定", true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.leftMargin = dp(act, 8);
        p.setLayoutParams(pp);
        p.setOnClickListener(v -> {
            String s = et.getText().toString().trim();
            d.dismiss();
            if (cb != null) cb.onInput(s);
        });
        btns.addView(p);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(act, 18);
        root.addView(btns, bp);

        d.setContentView(root);

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            // 窗口本身也清干净，免得露黑
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams dl = w.getAttributes();
            dl.dimAmount = 0.32f;
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.width = dp(act, 344);
            w.setAttributes(lp);
        }
        return d;
    }

    /**
     * 密码输入框 + 眼睛切换。
     *
     * 默认显示为圆点（••••），点右边的小眼睛才看得到明文。
     */
    public static Dialog inputSecret(Activity act, String title, String hint,
                                     String preset, OnInput cb) {
        Dialog d = new Dialog(act);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 22), dp(act, 22), dp(act, 22), dp(act, 16));
        LiquidGlass.glassPanel(root, 28, Theme.dark, true);

        TextView t = new TextView(act);
        t.setText(title);
        t.setTextSize(17.5f);
        t.setTextColor(Theme.text());
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(t);

        // 输入框 + 眼睛，放在一个圆角容器里
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);

        GradientDrawable eb = new GradientDrawable();
        eb.setCornerRadius(dp(act, 15));
        eb.setColor(Theme.dark ? 0x33FFFFFF : 0xFFF4F6F9);
        eb.setStroke(dp(act, 1), glassEdge());
        box.setBackground(eb);

        final android.widget.EditText et = new android.widget.EditText(act);
        et.setText(preset == null ? "" : preset);
        et.setHint(hint == null ? "" : hint);
        et.setHintTextColor(subText());
        et.setTextSize(15.5f);
        et.setTextColor(Theme.text());
        et.setSingleLine(true);
        et.setBackground(null);
        et.setPadding(dp(act, 16), dp(act, 13), dp(act, 6), dp(act, 13));
        et.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(et, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView eye = new TextView(act);
        eye.setText("👁");   // 眼睛
        eye.setTextSize(16);
        eye.setPadding(dp(act, 8), dp(act, 10), dp(act, 14), dp(act, 10));
        eye.setTextColor(subText());
        eye.setClickable(true);
        box.addView(eye);

        final boolean[] shown = new boolean[]{false};
        eye.setOnClickListener(v -> {
            shown[0] = !shown[0];
            int type = shown[0]
                    ? (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                    : (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            et.setInputType(type);
            // 光标挪到末尾
            try {
                et.setSelection(et.getText().length());
            } catch (Throwable ignored) {
            }
            eye.setAlpha(shown[0] ? 1f : 0.6f);
        });
        eye.setAlpha(0.6f);

        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = dp(act, 16);
        root.addView(box, ep);

        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);

        TextView n = pill(act, "取消", false);
        n.setOnClickListener(v -> d.dismiss());
        btns.addView(n);

        TextView p2 = pill(act, "确定", true);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.leftMargin = dp(act, 8);
        p2.setLayoutParams(pp);
        p2.setOnClickListener(v -> {
            String str = et.getText().toString().trim();
            d.dismiss();
            if (cb != null) cb.onInput(str);
        });
        btns.addView(p2);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = dp(act, 18);
        root.addView(btns, bp);

        d.setContentView(root);

        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams dl = w.getAttributes();
            dl.dimAmount = 0.32f;
            dl.width = ViewGroup.LayoutParams.MATCH_PARENT;
            dl.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            dl.gravity = Gravity.CENTER;
            w.setAttributes(dl);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        return d;
    }


    // ---------- 胶囊按钮 ----------
    public static TextView pill(Activity a, String text, boolean primary) {
        TextView t = new TextView(a);
        t.setText(text);
        t.setTextSize(14);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(a, 22), dp(a, 11), dp(a, 22), dp(a, 11));
        t.setTextColor(primary ? Color.WHITE : Theme.text());

        GradientDrawable g;
        if (primary) {
            g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF5AB8FF, 0xFF2E7BEA});
            g.setCornerRadius(dp(a, 100));
        } else {
            g = new GradientDrawable();
            g.setCornerRadius(dp(a, 100));
            g.setColor(Theme.dark ? 0x22FFFFFF : 0xFFEDF1F6);
        }
        t.setBackground(g);
        return t;
    }

    private static int dp(Activity a, int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density);
    }
}
