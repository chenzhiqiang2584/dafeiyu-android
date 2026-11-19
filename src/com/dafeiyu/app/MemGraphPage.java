package com.dafeiyu.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆图谱页面。
 *
 * 从 Memory 里把记忆摊平，建成图，用 MemGraphView 画出来。
 * 整页显示，顶部有「关闭」和「清空」，不挤在小卡片里。
 */
public final class MemGraphPage {

    private MemGraphPage() {
    }

    private static int dp(Activity a, int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static void show(final Activity act) {
        try {
            List<MemGraph.Node> raw = collect(act);
            if (raw.isEmpty()) {
                SimpleDialog.alert(act, "记忆图谱",
                        "还没有记忆。\n\n点设置里的「AI 分析并提取偏好」，先让她记住一些东西。");
                return;
            }

            final MemGraph g = new MemGraph();
            g.setMemories(raw);

            final android.app.Dialog dlg = new Dialog(act,
                    android.R.style.Theme_DeviceDefault_NoActionBar);
            try {
                dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Theme.bg()));
            } catch (Throwable ignored) {
            }

            FrameLayout root = new FrameLayout(act);
            root.setBackgroundColor(Theme.bg());

            final MemGraphView view = new MemGraphView(act);
            view.setDark(Theme.dark);
            view.setGraph(g);
            view.setOnNodeTap(n -> {
                if (n == null) {
                    return;
                }
                String body = n.body == null ? "" : n.body;
                MDialog.card(act, n.title == null ? "记忆" : n.title,
                        body + "\n\n分类：" + (n.track == null ? "-" : n.track)
                                + "\n权重：" + n.weight,
                        "知道了", null, null, null).show();
            });

            String info = "共 " + g.nodes.size() + " 条记忆，" + g.edges.size() + " 条连线";
            if (raw.size() > MemGraph.MAX_NODES) {
                info += "\n（超过 " + MemGraph.MAX_NODES + " 条，只画了权重最高的一批）";
            }

            // 顶部栏：标题 + 条数说明 + 清空 + 关闭
            LinearLayout bar = new LinearLayout(act);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setPadding(dp(act, 14), dp(act, 12), dp(act, 14), dp(act, 12));
            bar.setBackgroundColor(Theme.bar());

            LinearLayout titles = new LinearLayout(act);
            titles.setOrientation(LinearLayout.VERTICAL);
            bar.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

            TextView t1 = new TextView(act);
            t1.setText("记忆图谱");
            t1.setTextSize(16);
            t1.setTextColor(Theme.text());
            titles.addView(t1);

            TextView t2 = new TextView(act);
            t2.setText(info.replace("\n", " "));
            t2.setTextSize(11);
            t2.setTextColor(Theme.muted());
            t2.setPadding(0, dp(act, 2), 0, 0);
            titles.addView(t2);

            TextView clear = new TextView(act);
            clear.setText("清空");
            clear.setTextSize(14);
            clear.setTextColor(0xFFE05353);
            clear.setPadding(dp(act, 12), dp(act, 8), dp(act, 12), dp(act, 8));
            clear.setClickable(true);
            clear.setOnClickListener(v -> confirmClear(act, dlg));
            bar.addView(clear);

            TextView close = new TextView(act);
            close.setText("关闭");
            close.setTextSize(14);
            close.setTextColor(Theme.dark ? 0xFF6FB8FF : 0xFF1A73E8);
            close.setPadding(dp(act, 12), dp(act, 8), dp(act, 4), dp(act, 8));
            close.setClickable(true);
            close.setOnClickListener(v -> dlg.dismiss());
            bar.addView(close);

            // 图占满，顶部让开顶栏
            FrameLayout.LayoutParams vlp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            vlp.topMargin = dp(act, 56);
            root.addView(view, vlp);

            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            blp.gravity = Gravity.TOP;
            root.addView(bar, blp);

            dlg.setContentView(root);
            dlg.setOnShowListener(x -> {
                try {
                    dlg.getWindow().setLayout(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                } catch (Throwable ignored) {
                }
            });
            dlg.show();

            // 套上自定义字体（顶栏那些文字）
            try {
                FontKit.applyTo(act, root);
            } catch (Throwable ignored) {
            }

        } catch (Throwable t) {
            SimpleDialog.alert(act, "记忆图谱", "打开失败：" + t);
        }
    }

    /** 二次确认后清空所有记忆。 */
    private static void confirmClear(final Activity act, final Dialog dlg) {
        MDialog.card(act, "清空所有记忆？",
                "会删掉：\n"
                        + "· AI 分析出来的用户档案\n"
                        + "· 每晚整理的记忆\n"
                        + "· 在忙的事\n\n"
                        + "不能恢复。对话记录不受影响。",
                "清空", () -> {
                    try {
                        Memory m = new Memory(act);
                        m.clearTrack(Memory.USER);
                        m.clearTrack(Memory.MEMORY);
                        m.clearTrack(Memory.PROJECT);
                        m.clearTrack(Memory.KEY);
                        if (dlg != null) {
                            dlg.dismiss();
                        }
                        SimpleDialog.alert(act, "已清空", "记忆都删掉了。");
                    } catch (Throwable t) {
                        SimpleDialog.alert(act, "清空失败", "" + t);
                    }
                }, "算了", null).show();
    }

    /** 把 Memory 里的东西摊平成节点。 */
    public static List<MemGraph.Node> collect(Activity act) {
        List<MemGraph.Node> out = new ArrayList<MemGraph.Node>();
        try {
            Memory mem = new Memory(act);
            addTrack(mem, Memory.KEY, "关键", out);
            addTrack(mem, Memory.MEMORY, "记忆", out);
            addTrack(mem, Memory.USER, "档案", out);
            addTrack(mem, Memory.PROJECT, "项目", out);
            addTrack(mem, Memory.DAILY, "日常", out);
            String[] extra = {"mood", "fact", "pref"};
            for (String t : extra) {
                addTrack(mem, t, t, out);
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void addTrack(Memory mem, String track, String label, List<MemGraph.Node> out) {
        try {
            List<Memory.Entry> list = mem.list(track);
            if (list == null) {
                return;
            }
            for (Memory.Entry e : list) {
                if (e == null || e.title == null || e.title.trim().isEmpty()) {
                    continue;
                }
                MemGraph.Node n = new MemGraph.Node();
                n.title = e.title.trim();
                n.body = e.body;
                n.track = label;
                n.realTrack = track;
                n.at = e.at;
                n.weight = e.w;
                out.add(n);
            }
        } catch (Throwable ignored) {
        }
    }
}
