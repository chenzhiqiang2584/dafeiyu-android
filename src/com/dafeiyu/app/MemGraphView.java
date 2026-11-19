package com.dafeiyu.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆图谱视图：弹簧布局 + 手势缩放拖动。
 *
 * 布局只在尺寸变化时算一次，并且限制迭代次数 ——
 * 原来 90 次迭代 + 全量 O(n^2) 会把 UI 线程卡住好几秒。
 */
public final class MemGraphView extends View {

    public interface OnNodeTap {
        void onTap(MemGraph.Node n);
    }

    private final MemGraph graph = new MemGraph();
    private final Paint pEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pNode = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBg = new Paint(Paint.ANTI_ALIAS_FLAG);

    private OnNodeTap onTap;
    private boolean dark = false;
    private boolean laid = false;
    private float scale = 1f;
    private float panX = 0f, panY = 0f;
    private float lastX, lastY;
    private int mode = 0; // 0 无 1 拖 2 缩放
    private float pinchStart = 1f;

    public MemGraphView(Context c) {
        super(c);
        pEdge.setStrokeWidth(2f);
        pText.setTextAlign(Paint.Align.CENTER);
        pText.setTextSize(20f);
        pText.setTypeface(Typeface.DEFAULT);
    }

    public void setDark(boolean d) {
        dark = d;
        invalidate();
    }

    public void setOnNodeTap(OnNodeTap t) {
        onTap = t;
    }

    public void setGraph(MemGraph g) {
        if (g == null) {
            return;
        }
        graph.nodes.clear();
        graph.nodes.addAll(g.nodes);
        graph.edges.clear();
        graph.edges.addAll(g.edges);
        laid = false;
        invalidate();
    }

    // ---------- 布局 ----------

    private void compute(float w, float h) {
        List<MemGraph.Node> nodes = graph.nodes;
        int n = nodes.size();
        if (n == 0) {
            return;
        }
        // 黄金角撒点
        double golden = Math.PI * (3 - Math.sqrt(5));
        float minWH = Math.min(w, h);
        for (int i = 0; i < n; i++) {
            MemGraph.Node nd = nodes.get(i);
            double frac = n <= 1 ? 0 : (double) i / n;
            double rad = Math.sqrt(frac) * minWH * 0.40;
            double ang = i * golden;
            nd.x = w / 2f + (float) (Math.cos(ang) * rad);
            nd.y = h / 2f + (float) (Math.sin(ang) * rad);
            nd.r = 7f + (float) (clamp01(nd.weight) * 9f);
            nd.vx = 0;
            nd.vy = 0;
        }

        int iters = n > 60 ? 30 : 45;
        for (int it = 0; it < iters; it++) {
            // 弹簧
            for (MemGraph.Edge e : graph.edges) {
                MemGraph.Node a = nodes.get(e.a);
                MemGraph.Node b = nodes.get(e.b);
                float dx = b.x - a.x, dy = b.y - a.y;
                float d = (float) Math.max(1.0, Math.sqrt(dx * dx + dy * dy));
                float force = e.sim * (d - 120f) / d * 0.02f;
                a.vx += dx * force;
                a.vy += dy * force;
                b.vx -= dx * force;
                b.vy -= dy * force;
            }
            // 斥力
            for (int i = 0; i < n; i++) {
                MemGraph.Node a = nodes.get(i);
                for (int j = i + 1; j < n; j++) {
                    MemGraph.Node b = nodes.get(j);
                    float dx = b.x - a.x, dy = b.y - a.y;
                    float d2 = Math.max(64f, dx * dx + dy * dy);
                    float f = 260f / d2;
                    a.vx -= dx * f;
                    a.vy -= dy * f;
                    b.vx += dx * f;
                    b.vy += dy * f;
                }
            }
            for (MemGraph.Node nd : nodes) {
                nd.vx *= 0.72f;
                nd.vy *= 0.72f;
                nd.x += nd.vx;
                nd.y += nd.vy;
                nd.x = Math.max(nd.r + 8, Math.min(w - nd.r - 8, nd.x));
                nd.y = Math.max(nd.r + 8, Math.min(h - nd.r - 8, nd.y));
            }
        }
        laid = true;
    }

    private static float clamp01(double v) {
        if (v < 0) return 0f;
        if (v > 1) return 1f;
        return (float) v;
    }

    // ---------- 画 ----------

    @Override
    protected void onDraw(Canvas cv) {
        super.onDraw(cv);
        int w = getWidth(), h = getHeight();
        pBg.setColor(dark ? 0xFF10151C : 0xFFF7F9FB);
        cv.drawRect(0, 0, w, h, pBg);

        if (!laid && w > 0 && h > 0) {
            compute(w, h);
        }

        cv.save();
        cv.translate(panX, panY);
        cv.scale(scale, scale);

        List<MemGraph.Node> nodes = graph.nodes;

        // 边
        pEdge.setColor(dark ? 0x558FA6C0 : 0x4060748C);
        for (MemGraph.Edge e : graph.edges) {
            MemGraph.Node a = nodes.get(e.a);
            MemGraph.Node b = nodes.get(e.b);
            cv.drawLine(a.x, a.y, b.x, b.y, pEdge);
        }

        // 点
        for (MemGraph.Node n : nodes) {
            pNode.setColor(colorOf(n.track));
            cv.drawCircle(n.x, n.y, n.r, pNode);
        }

        // 标签（点多时只画权重大的一半）
        pText.setColor(dark ? 0xFFD6DEE8 : 0xFF3A4552);
        boolean dense = nodes.size() > 40;
        for (MemGraph.Node n : nodes) {
            if (dense && n.weight < 0.5) {
                continue;
            }
            String t = n.title;
            if (t == null) {
                continue;
            }
            if (t.length() > 8) {
                t = t.substring(0, 8) + "…";
            }
            cv.drawText(t, n.x, n.y - n.r - 6, pText);
        }

        cv.restore();
    }

    private int colorOf(String track) {
        if (track == null) {
            return 0xFF7FB3D5;
        }
        int h = Math.abs(track.hashCode());
        int idx = h % 6;
        int[] cols = {0xFF7FB3D5, 0xFF9BCB8C, 0xFFE8B062, 0xFFCB8FB8, 0xFF8FA6C0, 0xFFD98C8C};
        return cols[idx];
    }

    // ---------- 手势 ----------

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = ev.getX();
                lastY = ev.getY();
                mode = 1;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                if (ev.getPointerCount() >= 2) {
                    mode = 2;
                    pinchStart = dist(ev);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mode == 2 && ev.getPointerCount() >= 2) {
                    float d = dist(ev);
                    if (pinchStart > 1) {
                        float ns = scale * (d / pinchStart);
                        scale = Math.max(0.35f, Math.min(4f, ns));
                        pinchStart = d;
                        invalidate();
                    }
                } else if (mode == 1) {
                    panX += ev.getX() - lastX;
                    panY += ev.getY() - lastY;
                    lastX = ev.getX();
                    lastY = ev.getY();
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (mode == 1) {
                    tap(ev.getX(), ev.getY());
                }
                mode = 0;
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                mode = 0;
                return true;
            case MotionEvent.ACTION_CANCEL:
                mode = 0;
                return true;
        }
        return super.onTouchEvent(ev);
    }

    private float dist(MotionEvent e) {
        float dx = e.getX(1) - e.getX(0);
        float dy = e.getY(1) - e.getY(0);
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    private void tap(float sx, float sy) {
        float x = (sx - panX) / scale;
        float y = (sy - panY) / scale;
        MemGraph.Node best = null;
        float bestD = Float.MAX_VALUE;
        for (MemGraph.Node n : graph.nodes) {
            float dx = n.x - x, dy = n.y - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            float hit = Math.max(n.r + 16, 26);
            if (d < hit && d < bestD) {
                bestD = d;
                best = n;
            }
        }
        if (best != null && onTap != null) {
            onTap.onTap(best);
        }
    }

    /** 把视图放到最大可读的位置。 */
    public void reset() {
        scale = 1f;
        panX = 0f;
        panY = 0f;
        invalidate();
    }
}
