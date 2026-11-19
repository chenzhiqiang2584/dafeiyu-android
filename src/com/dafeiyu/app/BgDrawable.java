package com.dafeiyu.app;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * 等比裁切填满的背景图。
 *
 * 原来用 BitmapDrawable + Gravity.FILL，那是「拉伸填满」，
 * 竖图放到更窄长的屏幕上就会被横向拉宽、比例失真。
 *
 * 这里用 BitmapShader + 缩放取最大比例，多出来的部分裁掉，
 * 相当于 CSS 的 background-size: cover。
 */
public final class BgDrawable extends Drawable {

    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Matrix matrix = new Matrix();
    private final Bitmap bitmap;
    private int lastW = -1, lastH = -1;

    public BgDrawable(Bitmap bmp) {
        this.bitmap = bmp;
        if (bmp != null && !bmp.isRecycled()) {
            BitmapShader shader = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            paint.setShader(shader);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        Rect bounds = getBounds();
        int w = bounds.width();
        int h = bounds.height();
        if (w <= 0 || h <= 0) {
            return;
        }
        if (w != lastW || h != lastH) {
            fit(w, h);
            lastW = w;
            lastH = h;
        }
        canvas.drawRect(bounds, paint);
    }

    /** 计算 cover 缩放：取「填满」所需的最大比例，居中放置。 */
    private void fit(int w, int h) {
        float bw = bitmap.getWidth();
        float bh = bitmap.getHeight();
        if (bw <= 0 || bh <= 0) {
            return;
        }
        float scale = Math.max(w / bw, h / bh);
        float dx = (w - bw * scale) / 2f;
        float dy = (h - bh * scale) / 2f;
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
        return PixelFormat.OPAQUE;
    }
}
