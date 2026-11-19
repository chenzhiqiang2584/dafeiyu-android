package com.dafeiyu.app;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * 圆形头像。
 *
 * 原来只把 ImageView 的背景设成椭圆，图片本身还是方的盖在上面，
 * 所以看着是方的。这里用 BitmapShader + 圆形路径把图片裁成圆。
 */
public final class CircleImageDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final Bitmap bitmap;
    private final float ringWidth;
    private final int ringColor;
    private int lastW = -1, lastH = -1;

    public CircleImageDrawable(Bitmap bmp) {
        this(bmp, 0f, 0);
    }

    public CircleImageDrawable(Bitmap bmp, float ringWidth, int ringColor) {
        this.bitmap = bmp;
        this.ringWidth = ringWidth;
        this.ringColor = ringColor;
        if (bmp != null && !bmp.isRecycled()) {
            BitmapShader shader = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.setShader(shader);
        }
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(ringWidth);
        ring.setColor(ringColor);
    }

    @Override
    public void draw(Canvas cv) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        Rect b = getBounds();
        int w = b.width();
        int h = b.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        float r = Math.min(w, h) / 2f;

        // 圆内缩一点给描边留位置
        float inset = ringWidth / 2f;
        float cx = b.centerX();
        float cy = b.centerY();
        rect.set(cx - r + inset, cy - r + inset, cx + r - inset, cy + r - inset);

        int d = (int) (r * 2);
        if (d != lastW || d != lastH) {
            fit(d, d);
            lastW = d;
            lastH = d;
        }

        cv.save();
        cv.drawCircle(cx, cy, r - inset, paint);
        cv.restore();

        if (ringWidth > 0) {
            cv.drawCircle(cx, cy, r - inset, ring);
        }
    }

    /** 居中填满（cover），按圆形区域算。 */
    private void fit(int d, int d2) {
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        if (bw <= 0 || bh <= 0) {
            return;
        }
        Rect b = getBounds();
        float scale = Math.max(d / bw, d / bh);
        float dx = b.left + (b.width() - bw * scale) / 2f;
        float dy = b.top + (b.height() - bh * scale) / 2f;
        matrix.reset();
        matrix.setScale(scale, scale);
        matrix.postTranslate(dx, dy);
        Shader sh = paint.getShader();
        if (sh != null) {
            sh.setLocalMatrix(matrix);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
