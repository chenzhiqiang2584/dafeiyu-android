package com.dafeiyu.app;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

/**
 * 全局字体：把界面上的 TextView / EditText 全换成行楷。
 *
 * 注意：行楷只用于「显示」，输入框里的文字也换，方便统一观感。
 * 字体读一次就缓存，不要每次遍历时都 createFromAsset。
 */
public final class FontKit {

    private static final String ASSET = "fonts/xinwei.ttf";
    private static Typeface cached;
    private static boolean tried = false;

    private FontKit() {
    }

    /** 拿字体；读不到就返回 null（调用方跳过）。 */
    public static Typeface font(Activity act) {
        if (tried) {
            return cached;
        }
        tried = true;
        try {
            cached = Typeface.createFromAsset(act.getAssets(), ASSET);
        } catch (Throwable t) {
            cached = null;
        }
        return cached;
    }

    /** 给整棵视图树换字体。 */
    public static void apply(Activity act) {
        Typeface tf = font(act);
        if (tf == null) {
            return;
        }
        try {
            View root = act.findViewById(android.R.id.content);
            if (root != null) {
                walk(root, tf);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 只给单个 View 换。 */
    public static void applyTo(Activity act, View v) {
        Typeface tf = font(act);
        if (tf != null && v != null) {
            walk(v, tf);
        }
    }

    private static void walk(View v, Typeface tf) {
        if (v == null) {
            return;
        }
        try {
            if (v instanceof TextView) {
                TextView tv = (TextView) v;
                Typeface old = tv.getTypeface();
                if (old != null && old.isBold()) {
                    tv.setTypeface(tf, Typeface.BOLD);
                } else {
                    tv.setTypeface(tf);
                }
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    walk(g.getChildAt(i), tf);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /** 新建 TextView 时直接用这个。（能少一次遍历） */
    public static void r(Activity act, TextView tv) {
        Typeface tf = font(act);
        if (tf != null && tv != null) {
            try {
                tv.setTypeface(tf);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 字体到底有没有加载成功（排查用）。 */
    public static boolean loaded(Activity act) {
        return font(act) != null;
    }

    /** 字体文件大小，给设置页显示。 */
    public static String info(Activity act) {
        try {
            java.io.InputStream in = act.getAssets().open(ASSET);
            int n = in.available();
            in.close();
            Typeface tf = font(act);
            return "xinwei.ttf " + (n / 1024) + " KB, " + (tf != null ? "已加载" : "加载失败");
        } catch (Throwable t) {
            return "读不到字体：" + t.getClass().getSimpleName();
        }
    }
}
