package com.dafeiyu.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/**
 * 单色细线图标（图二风格）：叠层 / 竖纹 / 四角星 / 波浪 / 同心圆 / 音符 /
 * 人形 / 双方块 / 时钟 / 键盘 / 图片 / 面具 / 笔 / 箭头 …
 *
 * 注意：不要用 canvas.scale —— 它会把 strokeWidth 一起放大，图标会糊成实心块。
 * 这里改成手动坐标缩放，线宽固定。
 */
public class LineIconDrawable extends Drawable {

    public static final int LAYER = 0;     // 叠层（人格）
    public static final int SLIDERS = 1;   // 竖纹（配置）
    public static final int STAR = 2;      // 四角星（主题）
    public static final int WAVE = 3;      // 波浪（状态栏）
    public static final int TARGET = 4;    // 同心圆（Token）
    public static final int MUSIC = 5;     // 音符（音乐）
    public static final int PERSON = 6;    // 人形（用户）
    public static final int BLOCKS = 7;    // 双方块（气泡皮肤）
    public static final int CLOCK = 8;     // 时钟（定时消息）
    public static final int KEYBOARD = 9;  // 键盘（打字音效）
    public static final int PHOTO = 10;    // 图片
    public static final int MASK = 11;     // 面具（人格）
    public static final int PEN = 12;      // 笔（改名）
    public static final int KEY = 13;      // 钥匙
    public static final int SIGNAL = 14;   // 信号
    public static final int DOWNLOAD = 15; // 下载（导出）
    public static final int BROOM = 16;    // 扫帚（清空）
    public static final int GRAPH = 17;    // 折线
    public static final int BUBBLE = 18;   // 气泡
    public static final int CHEVRON = 19;  // >
    public static final int BRAIN = 20;    // 脑/记忆
    public static final int SPARK = 21;    // 小星
    public static final int CHART = 22;    // 柱状

    private int kind;
    private final int color;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private float px = 1f;

    public LineIconDrawable(int kind, int color) {
        this.kind = kind;
        this.color = color;
    }

    @Override
    public void draw(Canvas c) {
        p.reset();
        p.setAntiAlias(true);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);

        android.graphics.Rect b = getBounds();
        float s = Math.min(b.width(), b.height());
        if (s <= 0) return;
        // 线宽固定像素，不随图标大小/缩放变化
        p.setStrokeWidth(Math.max(1.2f, s / 22f));

        px = s / 24f;
        c.save();
        c.translate(b.exactCenterX() - s / 2f, b.exactCenterY() - s / 2f);
        drawShape(c);
        c.restore();
    }

    private void L(Canvas c, float... xy) {
        for (int n = 0; n + 1 < xy.length; n += 2) {
            if (n == 0) continue;
            c.drawLine(xy[n - 2] * px, xy[n - 1] * px, xy[n] * px, xy[n + 1] * px, p);
        }
    }

    private void ARC(Canvas c, float l, float t, float rt, float bt, float start, float sweep) {
        r.set(l * px, t * px, rt * px, bt * px);
        c.drawArc(r, start, sweep, false, p);
    }

    private void RR(Canvas c, float l, float t, float rt, float bt, float rad) {
        r.set(l * px, t * px, rt * px, bt * px);
        c.drawRoundRect(r, rad * px, rad * px, p);
    }

    private void CIRC(Canvas c, float cx, float cy, float rr) {
        c.drawCircle(cx * px, cy * px, rr * px, p);
    }

    private Path starPath(float cx, float cy, float s) {
        Path t = new Path();
        float k = s * 0.30f;
        t.moveTo(cx * px, (cy - s) * px);
        t.quadTo((cx + k) * px, (cy - k) * px, (cx + s) * px, cy * px);
        t.quadTo((cx + k) * px, (cy + k) * px, cx * px, (cy + s) * px);
        t.quadTo((cx - k) * px, (cy + k) * px, (cx - s) * px, cy * px);
        t.quadTo((cx - k) * px, (cy - k) * px, cx * px, (cy - s) * px);
        t.close();
        return t;
    }

    private void drawShape(Canvas c) {
        switch (kind) {
            case LAYER:
                RR(c, 4, 3.5f, 20, 9.5f, 2.5f);
                RR(c, 4, 10, 20, 16, 2.5f);
                RR(c, 4, 16.5f, 20, 22.5f, 2.5f);
                break;
            case SLIDERS:
                L(c, 4, 12, 20, 12);
                CIRC(c, 9, 12, 2.2f);
                CIRC(c, 15, 12, 2.2f);
                L(c, 4, 6.5f, 20, 6.5f);
                L(c, 4, 17.5f, 20, 17.5f);
                break;
            case STAR:
                c.drawPath(starPath(12, 12, 8.5f), p);
                break;
            case WAVE:
                c.drawPath(wavePath(), p);
                break;
            case TARGET:
                CIRC(c, 12, 12, 8.5f);
                CIRC(c, 12, 12, 4.8f);
                CIRC(c, 12, 12, 1.3f);
                break;
            case MUSIC:
                L(c, 9.5f, 18, 9.5f, 6.5f, 18, 4.5f);
                L(c, 18, 4.5f, 18, 15.5f);
                CIRC(c, 7, 18, 2.6f);
                CIRC(c, 15.5f, 15.5f, 2.6f);
                break;
            case PERSON:
                CIRC(c, 12, 8, 3.6f);
                ARC(c, 4.5f, 14, 19.5f, 26, 180, 180);
                break;
            case BLOCKS:
                RR(c, 3.5f, 5.5f, 13.5f, 14.5f, 3);
                RR(c, 10.5f, 9.5f, 20.5f, 18.5f, 3);
                break;
            case CLOCK:
                CIRC(c, 12, 12, 8.5f);
                L(c, 12, 6.5f, 12, 12, 15.5f, 14.5f);
                break;
            case KEYBOARD:
                RR(c, 2.5f, 7, 21.5f, 17, 3);
                L(c, 6, 10.5f, 6.01f, 10.5f);
                L(c, 9, 10.5f, 9.01f, 10.5f);
                L(c, 12, 10.5f, 12.01f, 10.5f);
                L(c, 15, 10.5f, 15.01f, 10.5f);
                L(c, 18, 10.5f, 18.01f, 10.5f);
                L(c, 6, 13.5f, 6.01f, 13.5f);
                L(c, 9, 13.5f, 9.01f, 13.5f);
                L(c, 12, 13.5f, 12.01f, 13.5f);
                L(c, 15, 13.5f, 15.01f, 13.5f);
                L(c, 18, 13.5f, 18.01f, 13.5f);
                L(c, 8, 16, 16, 16);
                break;
            case PHOTO:
                RR(c, 3.5f, 4.5f, 20.5f, 19.5f, 3);
                CIRC(c, 8.5f, 9.5f, 1.4f);
                L(c, 4.5f, 17, 9.5f, 12.5f, 13, 16, 16, 13, 19.5f, 17);
                break;
            case MASK:
                RR(c, 3.5f, 7, 20.5f, 17, 5);
                L(c, 8.5f, 11, 10, 12.5f);
                L(c, 15.5f, 11, 14, 12.5f);
                L(c, 9.5f, 14.5f, 14.5f, 14.5f);
                break;
            case PEN:
                L(c, 4, 20, 4, 16, 16, 4, 20, 8, 8, 20, 4, 20);
                L(c, 13, 7, 17, 11);
                break;
            case KEY:
                CIRC(c, 7.5f, 12, 4.2f);
                L(c, 11.7f, 12, 20.5f, 12);
                L(c, 17, 12, 17, 15.5f);
                L(c, 20.5f, 12, 20.5f, 15);
                break;
            case SIGNAL:
                L(c, 12, 20, 12, 6);
                L(c, 7.5f, 10.5f, 12, 5.5f, 16.5f, 10.5f);
                L(c, 6, 20, 18, 20);
                break;
            case DOWNLOAD:
                L(c, 4, 17, 4, 20, 20, 20, 20, 17);
                L(c, 12, 3, 12, 15);
                L(c, 7.5f, 10.5f, 12, 15, 16.5f, 10.5f);
                break;
            case BROOM:
                L(c, 13, 3, 20, 10);
                L(c, 11.5f, 4.5f, 3.5f, 20.5f);
                L(c, 18.5f, 11.5f, 3.5f, 20.5f);
                break;
            case GRAPH:
                L(c, 3.5f, 18, 9, 11.5f, 13, 15, 20.5f, 6);
                L(c, 16, 6, 20.5f, 6, 20.5f, 10.5f);
                break;
            case BUBBLE:
                RR(c, 3.5f, 4, 20.5f, 16, 4);
                L(c, 8, 16, 7, 20.5f, 12, 16);
                break;
            case CHEVRON:
                L(c, 9.5f, 5, 16.5f, 12, 9.5f, 19);
                break;
            case BRAIN:
                RR(c, 4, 4, 20, 9, 2);
                RR(c, 4, 10.5f, 20, 15.5f, 2);
                RR(c, 4, 17, 14, 21, 2);
                break;
            case SPARK:
                c.drawPath(starPath(12, 12, 7.5f), p);
                break;
            case CHART:
                L(c, 4, 20, 20, 20);
                L(c, 7.5f, 20, 7.5f, 12);
                L(c, 12, 20, 12, 7);
                L(c, 16.5f, 20, 16.5f, 14);
                break;
            default:
                CIRC(c, 12, 12, 8);
        }
    }

    /** 心电图/波浪线 */
    private Path wavePath() {
        Path t = new Path();
        t.moveTo(2.5f * px, 12 * px);
        t.quadTo(5.5f * px, 5 * px, 8.5f * px, 12 * px);
        t.quadTo(11.5f * px, 19 * px, 14.5f * px, 12 * px);
        t.quadTo(17.5f * px, 5 * px, 20.5f * px, 12 * px);
        return t;
    }

    @Override public void setAlpha(int a) {}
    @Override public void setColorFilter(ColorFilter cf) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
