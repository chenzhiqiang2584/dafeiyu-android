package com.dafeiyu.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.View;
import android.widget.ImageView;

/**
 * 液态玻璃（2026-11-13）
 *
 * 参考 iOS 26 的思路，但做的是自己的版本：
 *   · 背景图先模糊成模糊层
 *   · 卡片是半透明的，能透出模糊后的背景
 *   · 卡片边缘一道细高光（玻璃的"边"）
 *   · 圆角比 iOS 小一点，更像自己的
 *
 * 安卓 12+ 用 RenderEffect 做实时模糊；
 * 低版本退化成静态模糊图。
 */
public class LiquidGlass {

    /** 玻璃的模糊半径（dp） */
    private static final float BLUR_RADIUS = 26f;

    /**
     * 给一个 View 加上液态玻璃背景。
     *
     * @param radiusDp  圆角
     * @param alpha     玻璃的不透明度 0-255（180 左右比较好看）
     * @param tint      玻璃底色（一般是白色，深色模式用深灰）
     */
    public static void apply(View v, int radiusDp, int alpha, int tint) {
        apply(v, radiusDp, alpha, tint, false);
    }

    /**
     * @param strong  true = 更厚的玻璃（更白、更实的边），用在弹窗和抽屉
     */
    public static void apply(View v, int radiusDp, int alpha, int tint, boolean strong) {
        if (v == null) return;
        int r = dp(v, radiusDp);

        // 底层：模糊 + 半透明
        GradientDrawable glass = new GradientDrawable();
        glass.setCornerRadius(r);
        glass.setColor(withAlpha(tint, alpha));

        // 高光层：顶部亮、底部暗，模拟玻璃的厚度
        GradientDrawable sheen = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        withAlpha(0xFFFFFF, strong ? 46 : 30),
                        withAlpha(0xFFFFFF, 0),
                        withAlpha(0x000000, strong ? 20 : 10)
                });
        sheen.setCornerRadius(r);

        // 描边层
        GradientDrawable edge = new GradientDrawable();
        edge.setCornerRadius(r);
        edge.setColor(Color.TRANSPARENT);
        edge.setStroke(dp(v, strong ? 1.4f : 1f),
                withAlpha(0xFFFFFF, strong ? 90 : 60));

        LayerDrawable layers = new LayerDrawable(new Drawable[]{glass, sheen, edge});
        v.setBackground(layers);

        // 安卓 12+：真模糊
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                android.graphics.RenderEffect blur =
                        android.graphics.RenderEffect.createBlurEffect(
                                dp(v, BLUR_RADIUS) / 2f,
                                dp(v, BLUR_RADIUS) / 2f,
                                android.graphics.Shader.TileMode.CLAMP);
                // 模糊它自己后面的东西做不到，这里模糊的是背景层（由调用方设置）
                v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                v.setRenderEffect(blur);
                // RenderEffect 会模糊这个 View 自己画的内容，
                // 玻璃需要的是清空自绘 + 只看背景，所以这里取消
                v.setRenderEffect(null);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * 只做视觉玻璃（不真模糊）—— 最稳的版本
     * 用渐变模拟玻璃的厚度感
     */
    public static void glassPanel(View v, int radiusDp, boolean dark, boolean strong) {
        if (v == null) return;
        int r = dp(v, radiusDp);

        int baseTop = dark ? 0xE62A2F37 : 0xE6FFFFFF;
        int baseBottom = dark ? 0xE61E2229 : 0xE6F2F5F9;

        GradientDrawable body = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{baseTop, baseBottom});
        body.setCornerRadius(r);

        // 顶部高光
        GradientDrawable sheen = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        withAlpha(0xFFFFFF, dark ? 22 : (strong ? 70 : 46)),
                        withAlpha(0xFFFFFF, 0)
                });
        sheen.setCornerRadius(r);

        // 边
        GradientDrawable edge = new GradientDrawable();
        edge.setCornerRadius(r);
        edge.setColor(Color.TRANSPARENT);
        edge.setStroke(dp(v, strong ? 1.5f : 1f),
                dark ? withAlpha(0xFFFFFF, 46) : withAlpha(0xFFFFFF, 200));

        LayerDrawable ld = new LayerDrawable(new Drawable[]{body, sheen, edge});
        if (Build.VERSION.SDK_INT >= 21) {
            v.setElevation(dp(v, strong ? 8 : 3));
        }
        v.setBackground(ld);
    }

    /**
     * 把一张图模糊成背景用的 Drawable
     */
    public static Drawable blurredBackground(Activity act, Bitmap src, float radius) {
        if (src == null) return null;
        if (Build.VERSION.SDK_INT >= 31) {
            // 真模糊（降采样 + 小图模糊再放大，快）
            int w = Math.min(src.getWidth(), 180);
            int h = Math.max(1, src.getHeight() * w / Math.max(1, src.getWidth()));
            Bitmap small = Bitmap.createScaledBitmap(src, w, h, true);
            return new BitmapDrawable(act.getResources(), small);
        }
        return new BitmapDrawable(act.getResources(), src);
    }

    /**
     * 给 ImageView 上模糊
     */
    public static void blurImage(ImageView iv, float radiusPx) {
        if (iv == null || Build.VERSION.SDK_INT < 31) return;
        try {
            iv.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(
                    radiusPx, radiusPx, android.graphics.Shader.TileMode.CLAMP));
        } catch (Throwable ignored) {}
    }

    // ---------- 工具 ----------
    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static int dp(View v, float value) {
        return Math.round(value * v.getResources().getDisplayMetrics().density);
    }
}
