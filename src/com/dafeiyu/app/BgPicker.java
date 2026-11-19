package com.dafeiyu.app;

import android.app.Activity;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 聊天背景选择：九宫格内置图 + 从相册挑
 * 选中后回调 onPick(key)，key = "preset:xxx" 或 "album"
 */
public class BgPicker {

    /** 内置背景清单：键、名字、资源名 */
    public static final String[][] PRESETS = {
            {"white",  "纯白",    "bg_w_white"},
            {"wave",   "蓝色波纹", "bg_w_wave"},
            {"heart",  "爱心",    "bg_w_heart"},
            {"grain",  "白纹",    "bg_w_grain"},
            {"damask", "花纹",    "bg_w_damask"},
            {"warm",   "暖色",    "bg_w_warm"},
            {"stars",  "星空",    "bg_w_stars"},
            {"black",  "皮革黑",  "bg_w_black"},
            {"aurora", "极光",    "bg_aurora"},
            {"sakura", "樱花",    "bg_sakura"},
            {"night",  "夜色",    "bg_night"},
    };

    public interface OnPick {
        void onPreset(String key);   // white / wave / ...
        void onAlbum();              // 从相册
    }

    private final Activity act;
    private final OnPick onPick;
    private View root;
    private LinearLayout sheet;
    private String current;

    public BgPicker(Activity a, String currentKey, OnPick cb) {
        this.act = a;
        this.current = currentKey;
        this.onPick = cb;
        build();
    }

    public View getView() { return root; }

    public ViewGroup.LayoutParams defaultParams() {
        return new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public boolean isShown() { return root != null && root.getVisibility() == View.VISIBLE; }

    public void show() {
        if (root == null) return;
        root.setVisibility(View.VISIBLE);
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(160).start();
        if (sheet != null) {
            sheet.setTranslationY(act.getResources().getDisplayMetrics().heightPixels * 0.08f);
            sheet.animate().translationY(0).setDuration(220).start();
        }
    }

    public void hide() {
        if (root == null) return;
        final View v = root;
        v.animate().alpha(0f).setDuration(140).withEndAction(() -> {
            // 关键：从视图树里摘掉，不要只设 GONE ——
            // 留着它会跟后面重建的设置抽屉抢层级
            try {
                v.setVisibility(View.GONE);
                if (v.getParent() instanceof ViewGroup) {
                    ((ViewGroup) v.getParent()).removeView(v);
                }
            } catch (Throwable ignored) {}
        }).start();
    }

    /** 资源名找 id */
    public static int drawableId(Activity a, String name) {
        return a.getResources().getIdentifier(name, "drawable", a.getPackageName());
    }

    private void build() {
        FrameLayout wrap = new FrameLayout(act);
        wrap.setVisibility(View.GONE);

        // 遮罩
        View mask = new View(act);
        mask.setBackgroundColor(0x66000000);
        mask.setOnClickListener(v -> hide());
        wrap.addView(mask, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 底部面板
        sheet = new LinearLayout(act);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.dark ? 0xF21A2740 : 0xFAFFFFFF);
        bg.setCornerRadii(new float[]{dp(26), dp(26), dp(26), dp(26), 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(dp(18), dp(14), dp(18), dp(14));

        // 标题行
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(act);
        t.setText("聊天背景");
        t.setTextSize(17);
        t.setTextColor(Theme.text());
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD, android.graphics.Typeface.BOLD);
        t.getPaint().setFakeBoldText(true);
        head.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView close = new TextView(act);
        close.setText("收起");
        close.setTextSize(14);
        close.setTextColor(Theme.dark ? 0xFF7FC4FF : 0xFF2E7BEA);
        close.setPadding(dp(10), dp(4), dp(6), dp(4));
        close.setOnClickListener(v -> hide());
        head.addView(close);
        sheet.addView(head);

        ScrollView sv = new ScrollView(act);
        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(0, dp(10), 0, 0);
        sv.addView(body);

        TextView label = new TextView(act);
        label.setText("内置背景 · " + PRESETS.length + " 张");
        label.setTextSize(12.5f);
        label.setTextColor(Theme.muted());
        label.setPadding(0, dp(2), 0, dp(8));
        body.addView(label);

        // 九宫格
        GridLayout grid = new GridLayout(act);
        grid.setColumnCount(3);
        for (String[] p : PRESETS) {
            int id = drawableId(act, p[2]);
            grid.addView(cell(p[0], p[1], id), cellParams());
        }
        body.addView(grid);

        // 从相册
        TextView album = new TextView(act);
        album.setText("＋ 从相册选一张");
        album.setTextSize(14.5f);
        album.setGravity(Gravity.CENTER);
        album.setPadding(0, dp(13), 0, dp(13));
        GradientDrawable ab = new GradientDrawable();
        ab.setCornerRadius(dp(18));
        ab.setColor(Theme.dark ? 0x2E7FC4FF : 0x1A2E7BEA);
        ab.setStroke(dp(1), Theme.dark ? 0x667FC4FF : 0x552E7BEA);
        album.setBackground(ab);
        album.setTextColor(Theme.dark ? 0xFF7FC4FF : 0xFF2E7BEA);
        album.setOnClickListener(v -> { hide(); if (onPick != null) onPick.onAlbum(); });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = dp(12);
        body.addView(album, alp);

        sheet.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (int) (act.getResources()
                .getDisplayMetrics().heightPixels * 0.62f));
        sp.gravity = Gravity.BOTTOM;
        wrap.addView(sheet, sp);

        sp.gravity = Gravity.BOTTOM;
        root = wrap;
    }

    private GridLayout.LayoutParams cellParams() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        return lp;
    }

    /** 一格：图 + 名字 */
    private View cell(String key, String name, int resId) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        FrameLayout thumbWrap = new FrameLayout(act);
        ImageView iv = new ImageView(act);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (resId != 0) iv.setImageResource(resId);
        else iv.setBackgroundColor(0xFFDDDDDD);
        GradientDrawable round = new GradientDrawable();
        round.setCornerRadius(dp(14));
        round.setColor(Color.TRANSPARENT);
        iv.setBackground(round);
        // 圆角裁剪
        if (android.os.Build.VERSION.SDK_INT >= 21) iv.setClipToOutline(true);
        thumbWrap.addView(iv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 选中描边
        boolean on = current != null && current.equals("preset:" + key);
        GradientDrawable frame = new GradientDrawable();
        frame.setCornerRadius(dp(14));
        frame.setColor(Color.TRANSPARENT);
        frame.setStroke(dp(on ? 3 : 1), on
                ? (Theme.dark ? 0xFF7FC4FF : 0xFF2E7BEA)
                : (Theme.dark ? 0x33FFFFFF : 0x1A000000));
        View border = new View(act);
        border.setBackground(frame);
        thumbWrap.addView(border, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(78));
        box.addView(thumbWrap, tp);

        TextView nm = new TextView(act);
        nm.setText(name);
        nm.setTextSize(11.5f);
        nm.setGravity(Gravity.CENTER);
        nm.setTextColor(on ? (Theme.dark ? 0xFF7FC4FF : 0xFF2E7BEA) : Theme.muted());
        nm.setPadding(0, dp(4), 0, 0);
        box.addView(nm);

        box.setOnClickListener(v -> {
            current = "preset:" + key;
            hide();
            if (onPick != null) onPick.onPreset(key);
        });
        return box;
    }

    private int dp(int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density);
    }
}
