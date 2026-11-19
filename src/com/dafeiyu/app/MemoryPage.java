package com.dafeiyu.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 记忆整页。
 *
 * 之前是个小弹窗，内容一多就挤不下。
 * 现在占满屏幕，可以往下滚。
 */
public final class MemoryPage {

    private MemoryPage() {
    }

    private static int dp(Activity a, int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 一条待显示的记忆：轨道 + 在该轨里的原始下标 + 内容 */
    private static final class Row {
        String track;
        int index;
        Memory.Entry entry;
    }

    public static void open(final Activity act) {
        final Dialog dlg = new Dialog(act, android.R.style.Theme_DeviceDefault_NoActionBar);
        try {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Theme.bg()));
        } catch (Throwable ignored) {
        }

        final FrameLayout root = new FrameLayout(act);
        root.setBackgroundColor(Theme.bg());
        final ScrollView sv = new ScrollView(act);
        sv.setFillViewport(true);
        final LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(act, 14);
        list.setPadding(pad, dp(act, 64), pad, dp(act, 30));

        // 一次性读出所有条目，并记录各自在文件里的位置
        final java.util.List<Row> rows = new java.util.ArrayList<Row>();
        try {
            Memory m = new Memory(act);
            collect(m, Memory.USER, rows);
            collect(m, Memory.PROJECT, rows);
            collect(m, Memory.KEY, rows);
        } catch (Throwable ignored) {
        }

        final boolean[] selectMode = new boolean[]{false};
        final java.util.Set<Integer> selected = new java.util.HashSet<Integer>();

        sv.addView(list);
        root.addView(sv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout bar = new LinearLayout(act);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(pad, dp(act, 12), pad, dp(act, 12));
        bar.setBackgroundColor(Theme.bar());

        TextView title = new TextView(act);
        title.setText("记忆");
        title.setTextSize(16);
        title.setTextColor(Theme.text());
        bar.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView pick = new TextView(act);
        pick.setText("选择");
        pick.setTextSize(14);
        pick.setTextColor(Theme.dark ? 0xFF6FB8FF : 0xFF1A73E8);
        pick.setPadding(dp(act, 12), dp(act, 6), dp(act, 12), dp(act, 6));
        pick.setClickable(true);
        bar.addView(pick);

        final TextView clear = new TextView(act);
        clear.setText("清空");
        clear.setTextSize(14);
        clear.setTextColor(0xFFE05353);
        clear.setPadding(dp(act, 12), dp(act, 6), dp(act, 12), dp(act, 6));
        clear.setClickable(true);
        clear.setOnClickListener(v -> confirmClear(act, dlg, root));
        bar.addView(clear);

        TextView close = new TextView(act);
        close.setText("关闭");
        close.setTextSize(14);
        close.setTextColor(Theme.dark ? 0xFF6FB8FF : 0xFF1A73E8);
        close.setPadding(dp(act, 12), dp(act, 6), dp(act, 4), dp(act, 6));
        close.setOnClickListener(v -> dlg.dismiss());
        bar.addView(close);

        final LinearLayout listRef = list;
        final TextView titleRef = title;
        final TextView pickRef = pick;
        final TextView clearRef = clear;

        pick.setOnClickListener(v -> {
            if (!selectMode[0]) {
                selectMode[0] = true;
                selected.clear();
            } else {
                if (selected.isEmpty()) {
                    // 没选就退出
                    selectMode[0] = false;
                } else {
                    confirmDelete(act, rows, selected, selectMode, listRef,
                            titleRef, pickRef, clearRef);
                    return;
                }
            }
            render(act, listRef, rows, selectMode[0], selected,
                    titleRef, pickRef, clearRef);
        });

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

        render(act, list, rows, false, selected, title, pick, clear);

        try {
            FontKit.applyTo(act, root);
        } catch (Throwable ignored) {
        }
    }

    /** 把某一轨的条目收进来，记下原始下标。 */
    private static void collect(Memory m, String track, java.util.List<Row> out) {
        try {
            List<Memory.Entry> list = m.list(track);
            for (int i = 0; i < list.size(); i++) {
                Row r = new Row();
                r.track = track;
                r.index = i;
                r.entry = list.get(i);
                out.add(r);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 重画列表。 */
    private static void render(final Activity act, final LinearLayout list,
                               final java.util.List<Row> rows,
                               final boolean selectMode,
                               final java.util.Set<Integer> selected,
                               final TextView titleView, final TextView pick,
                               final TextView clear) {
        list.removeAllViews();

        // 顶栏状态
        if (selectMode) {
            titleView.setText("记忆 · 勾选要删的");
            pick.setText(selected.isEmpty() ? "删除" : "删除(" + selected.size() + ")");
            pick.setTextColor(0xFFE05353);
            clear.setVisibility(android.view.View.GONE);
        } else {
            titleView.setText("记忆");
            pick.setText("选择");
            pick.setTextColor(Theme.dark ? 0xFF6FB8FF : 0xFF1A73E8);
            clear.setVisibility(android.view.View.VISIBLE);
        }

        if (rows.isEmpty()) {
            section(act, list, "记忆", 0, null);
            empty(act, list, "还没有。\n\n"
                    + "点设置里的「AI 分析并提取偏好」，"
                    + "她会把关于你的偏好、习惯提炼出来存下。");
            try {
                FontKit.applyTo(act, list);
            } catch (Throwable ignored) {
            }
            return;
        }

        // 按轨道分段
        String lastTrack = null;
        int count = 0;
        for (Row r : rows) {
            if (r.track.equals(Memory.USER) && !Memory.USER.equals(lastTrack)) {
                count = countOf(rows, Memory.USER);
                section(act, list, "记忆", count,
                        "由「AI 分析并提取偏好」提炼出来，聊到相关的会自动用上");
                lastTrack = Memory.USER;
            } else if (r.track.equals(Memory.PROJECT) && !Memory.PROJECT.equals(lastTrack)) {
                count = countOf(rows, Memory.PROJECT);
                section(act, list, "在忙的事", count, null);
                lastTrack = Memory.PROJECT;
            } else if (r.track.equals(Memory.KEY) && !Memory.KEY.equals(lastTrack)) {
                count = countOf(rows, Memory.KEY);
                section(act, list, "关键记忆", count, "每轮对话都会带上，永不遗忘");
                lastTrack = Memory.KEY;
            }

            final Row row = r;
            final Runnable onToggle = () -> {
                pick.setText(selected.isEmpty()
                        ? "删除" : "删除(" + selected.size() + ")");
            };
            card(act, list, row.entry, row.track, row.index, selectMode, selected, onToggle);
        }

        // 重画之后所有控件都是新创建的对象，必须重新套一次字体
        try {
            FontKit.applyTo(act, list);
        } catch (Throwable ignored) {
        }
    }

    private static int countOf(java.util.List<Row> rows, String track) {
        int n = 0;
        for (Row r : rows) {
            if (r.track.equals(track)) {
                n++;
            }
        }
        return n;
    }

    /** 删除前确认。 */
    private static void confirmDelete(final Activity act,
                                      final java.util.List<Row> rows,
                                      final java.util.Set<Integer> selected,
                                      final boolean[] selectMode,
                                      final LinearLayout list,
                                      final TextView titleView, final TextView pick,
                                      final TextView clear) {
        final int n = selected.size();
        if (n == 0) {
            return;
        }
        MDialog.card(act, "删除选中的 " + n + " 条？",
                "删掉之后就不能恢复了。\n\n记忆图谱里也会一起消失。",
                "删除", () -> {
                    int ok = 0;
                    // 按轨道分组、倒序删（防下标错位）
                    java.util.Map<String, java.util.List<Integer>> byTrack =
                            new java.util.HashMap<String, java.util.List<Integer>>();
                    for (Row r : rows) {
                        int id = r.track.hashCode() * 100000 + r.index;
                        if (!selected.contains(id)) {
                            continue;
                        }
                        java.util.List<Integer> l = byTrack.get(r.track);
                        if (l == null) {
                            l = new java.util.ArrayList<Integer>();
                            byTrack.put(r.track, l);
                        }
                        l.add(r.index);
                    }

                    try {
                        Memory m = new Memory(act);
                        for (java.util.Map.Entry<String, java.util.List<Integer>> en
                                : byTrack.entrySet()) {
                            java.util.List<Integer> idxs = en.getValue();
                            java.util.Collections.sort(idxs,
                                    java.util.Collections.reverseOrder());
                            for (Integer idx : idxs) {
                                if (m.removeAtIndex(en.getKey(), idx)) {
                                    ok++;
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }

                    selected.clear();
                    selectMode[0] = false;

                    // 重新读一遍数据再画
                    java.util.List<Row> fresh = new java.util.ArrayList<Row>();
                    try {
                        Memory m2 = new Memory(act);
                        collect(m2, Memory.USER, fresh);
                        collect(m2, Memory.PROJECT, fresh);
                        collect(m2, Memory.KEY, fresh);
                    } catch (Throwable ignored) {
                    }
                    rows.clear();
                    rows.addAll(fresh);
                    render(act, list, rows, false, selected, titleView, pick, clear);

                    SimpleDialog.alert(act, "删掉了", "共删 " + ok + " 条。");
                }, "算了", null).show();
    }







    /** 分段标题。 */
    private static void section(Activity act, LinearLayout parent, String title, int count, String hint) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(act, 4), dp(act, 20), dp(act, 4), dp(act, 8));

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = new TextView(act);
        t.setText(title);
        t.setTextSize(15);
        t.setTextColor(Theme.text());
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(t);

        TextView n = new TextView(act);
        n.setText("  " + count);
        n.setTextSize(12.5f);
        n.setTextColor(Theme.muted());
        row.addView(n);

        box.addView(row);

        if (hint != null) {
            TextView h = new TextView(act);
            h.setText(hint);
            h.setTextSize(11.5f);
            h.setTextColor(Theme.muted());
            h.setPadding(0, dp(act, 3), 0, 0);
            box.addView(h);
        }
        parent.addView(box);
    }

    /** 一条记忆卡片。 */
    /**
     * 一条记忆卡片。
     * selectMode 为 true 时左侧显示勾选框，点整张卡片切换选中。
     */
    private static void card(final Activity act, LinearLayout parent,
                             final Memory.Entry e, final String track, final int origIndex,
                             boolean selectMode,
                             final java.util.Set<Integer> selected,
                             final Runnable onToggle) {
        String title = e.title == null ? "" : e.title.trim();
        String body = e.body == null ? "" : e.body.trim();
        final Integer idxId = track.hashCode() * 100000 + origIndex;

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(act, 10), dp(act, 10), dp(act, 12), dp(act, 10));

        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(act, 14));
        g.setColor(Theme.dark ? 0xFF22262D : 0xFFFFFFFF);
        if (Theme.dark) {
            g.setStroke(dp(act, 1), 0x22FFFFFF);
        }
        row.setBackground(g);

        // 选中状态
        final boolean[] checked = new boolean[]{
                selectMode && selected.contains(idxId)};

        if (selectMode) {
            final TextView box = new TextView(act);
            box.setTextSize(15);
            box.setGravity(Gravity.CENTER);
            box.setPadding(0, 0, dp(act, 8), 0);
            box.setText(checked[0] ? "\u2611" : "\u2610");
            box.setTextColor(checked[0]
                    ? (Theme.dark ? 0xFF6FB8FF : 0xFF1A73E8)
                    : Theme.muted());
            row.addView(box, new LinearLayout.LayoutParams(
                    dp(act, 26), ViewGroup.LayoutParams.WRAP_CONTENT));

            // 背景随选中变化
            final android.graphics.drawable.GradientDrawable g2 =
                    new android.graphics.drawable.GradientDrawable();
            g2.setCornerRadius(dp(act, 14));

            row.setClickable(true);
            row.setOnClickListener(v -> {
                checked[0] = !checked[0];
                box.setText(checked[0] ? "\u2611" : "\u2610");
                box.setTextColor(checked[0]
                        ? (Theme.dark ? 0xFF6FB8FF : 0xFF1A73E8)
                        : Theme.muted());
                if (checked[0]) {
                    selected.add(idxId);
                    g2.setColor(Theme.dark ? 0xFF2A3A52 : 0xFFE8F1FB);
                } else {
                    selected.remove(idxId);
                    g2.setColor(Theme.dark ? 0xFF22262D : 0xFFFFFFFF);
                }
                row.setBackground(g2);
                if (onToggle != null) {
                    onToggle.run();
                }
            });

            g2.setColor(checked[0]
                    ? (Theme.dark ? 0xFF2A3A52 : 0xFFE8F1FB)
                    : (Theme.dark ? 0xFF22262D : 0xFFFFFFFF));
            if (Theme.dark) {
                g2.setStroke(dp(act, 1), 0x22FFFFFF);
            }
            row.setBackground(g2);
        }

        // 文字
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        row.addView(c, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView t = new TextView(act);
        t.setText(title);
        t.setTextSize(14.5f);
        t.setTextColor(Theme.text());
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setLineSpacing(dp(act, 2), 1.2f);
        c.addView(t);

        if (!body.isEmpty() && !body.equals(title)) {
            TextView b = new TextView(act);
            b.setText(body);
            b.setTextSize(13);
            b.setTextColor(Theme.muted());
            b.setLineSpacing(dp(act, 3), 1.25f);
            b.setPadding(0, dp(act, 5), 0, 0);
            c.addView(b);
        }

        if (e.at != null && !e.at.trim().isEmpty()) {
            TextView tm = new TextView(act);
            tm.setText(e.at);
            tm.setTextSize(10.5f);
            tm.setTextColor(Theme.dark ? 0x77C4D2E2 : 0x99333A44);
            tm.setPadding(0, dp(act, 6), 0, 0);
            c.addView(tm);
        }

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(act, 8);
        parent.addView(row, lp);
    }

    /** 删之前确认一下。 */
    private static void confirmDelete(final Activity act, final Memory.Entry e,
                                      final String track, final Runnable onDeleted) {
        MDialog.card(act, "删掉这条？",
                (e.title == null ? "" : e.title),
                "删掉", () -> {
                    try {
                        boolean ok = new Memory(act).removeEntry(track,
                                e.title, e.at);
                        if (ok) {
                            if (onDeleted != null) {
                                onDeleted.run();
                            }
                        } else {
                            SimpleDialog.alert(act, "没删掉", "这条可能已经被改过了。");
                        }
                    } catch (Throwable t) {
                        SimpleDialog.alert(act, "删不掉", "" + t);
                    }
                }, "算了", null).show();
    }

    private static void empty(Activity act, LinearLayout parent, String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(13);
        t.setTextColor(Theme.muted());
        t.setLineSpacing(dp(act, 4), 1.3f);
        t.setPadding(dp(act, 6), dp(act, 10), dp(act, 6), dp(act, 10));
        parent.addView(t);
    }

    /** 二次确认后清空所有记忆。 */
    private static void confirmClear(final Activity act, final Dialog dlg, final android.view.View root) {
        MDialog.card(act, "清空所有记忆？",
                "会删掉：\n"
                        + "· AI 分析出来的用户档案\n"
                        + "· 记忆\n"
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
}
