package com.dafeiyu.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.os.SystemClock;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

/**
 * 能放 GIF 的 ImageView。
 *
 * BitmapFactory.decodeFile 解不了 GIF 动图（只会返回第一帧或 null），
 * 所以 GIF 走 android.graphics.Movie，静态图还是走 Bitmap。
 */
public final class GifView extends ImageView {

    private Movie movie;
    private long startAt;
    private Bitmap still;
    private boolean loaded;

    public GifView(Context c) {
        super(c);
    }

    /** 指定图片来源（本地文件）。 */
    public void setSource(File f) {
        if (f == null || !f.isFile()) {
            return;
        }
        String name = f.getName().toLowerCase();
        if (name.endsWith(".gif")) {
            try {
                FileInputStream in = new FileInputStream(f);
                movie = Movie.decodeStream(in);
                in.close();
            } catch (Throwable t) {
                movie = null;
            }
            if (movie != null) {
                startAt = SystemClock.uptimeMillis();
                loaded = true;
                postInvalidate();
                return;
            }
        }
        // 静态图
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 1;
            still = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable t) {
            still = null;
        }
        loaded = still != null;
        if (loaded) {
            post(() -> setImageBitmap(still));
        }
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (movie == null) {
            super.onDraw(cv);
            return;
        }
        try {
            long now = SystemClock.uptimeMillis();
            int dur = movie.duration();
            if (dur <= 0) {
                dur = 1000;
            }
            int pos = (int) ((now - startAt) % dur);
            movie.setTime(pos);

            int mw = movie.width();
            int mh = movie.height();
            if (mw <= 0 || mh <= 0) {
                super.onDraw(cv);
                return;
            }
            // 等比铺满这个 View（FIT_CENTER）
            float sx = (float) getWidth() / mw;
            float sy = (float) getHeight() / mh;
            float s = Math.min(sx, sy);
            float dx = (getWidth() - mw * s) / 2f;
            float dy = (getHeight() - mh * s) / 2f;

            int save = cv.save();
            cv.translate(dx, dy);
            cv.scale(s, s);
            movie.draw(cv, 0, 0);
            cv.restoreToCount(save);

            postInvalidateOnAnimation();
        } catch (Throwable t) {
            super.onDraw(cv);
        }
    }

    public boolean isLoaded() {
        return loaded;
    }
}
