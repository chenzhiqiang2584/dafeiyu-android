package com.dafeiyu.app;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 聊天记录存档。
 *
 * 每天的对话追加到 Documents/大肥鱼聊天记录/2026-11-19.md。
 * 用追加模式，不重写整个文件 —— 避免写一半被杀把历史搞丢。
 *
 * Android 10+ 走 MediaStore，10 以下直接写 /sdcard。
 */
public final class ChatArchive {

    private static final Object LOCK = new Object();
    /** 最近一次传入的 Context，供 root() 这类没参数的地方用 */
    private static Context appCtx;
    /** 当前写入的目标会话（append 时确定） */
    private static String curSession;
    private static final String DIR_NAME = "大肥鱼聊天记录";
    /** 全部聊天记录合成这一个文件 */
    public static final String ALL_FILE = "聊天记录.md";

    private ChatArchive() {
    }

    /** 存档根目录（可能为 null，表示拿不到）。 */
    /** 某个会话的存档目录（和 writeTo 保持一致）。 */
    private static File rootOf(Context c, String sessionId) {
        return new File(Session.dir(c, sessionId), DIR_NAME);
    }

    /** 存档目录：按会话分，App 私有。 */
    public static File root() {
        return root(appCtx);
    }

    public static File root(Context ctx) {
        try {
            if (ctx == null) {
                return null;
            }
            // 优先用 useSession 指定的会话，其次才取当前会话
            String sid = curSession;
            if (sid == null || sid.isEmpty()) {
                sid = Session.current(ctx.getApplicationContext());
            }
            File d = new File(Session.dir(ctx.getApplicationContext(), sid), DIR_NAME);
            if (!d.exists()) {
                d.mkdirs();
            }
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String dayName(long at) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(at));
    }

    /**
     * 追加一条消息。
     *
     * @param role    "user" / "assistant"
     * @param content 内容（表情会写成 [表情]）
     * @param at      时间戳
     */
    /**
     * 明确指定写到哪个会话，不依赖任何共享状态。
     * 这是主路径 —— Chat 会用它，保证消息一定进对的会话。
     */
    public static void appendTo(Context ctx, String sessionId,
                                String role, String content, long at) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        if (sessionId == null || sessionId.isEmpty()) {
            return;
        }
        if (ctx == null) {
            return;
        }
        appCtx = ctx.getApplicationContext();
        synchronized (LOCK) {
            try {
                writeTo(appCtx, sessionId, role, content, at);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void append(Context ctx, String role, String content, long at) {
        if (content == null || content.trim().isEmpty()) {
            return;
        }
        if (ctx != null) {
            appCtx = ctx.getApplicationContext();
        }
        if (curSession == null || curSession.isEmpty()) {
            if (ctx != null) {
                curSession = Session.current(ctx.getApplicationContext());
            }
        }
        final String sid = curSession;
        synchronized (LOCK) {
            try {
                writeTo(appCtx, sid, role, content, at);
            } catch (Throwable ignored) {
            }
        }
    }

    /** 真正的写入逻辑：指定会话、指定内容。 */
    private static void writeTo(Context ctx, String sessionId,
                                String role, String content, long at) throws Exception {
        if (ctx == null || sessionId == null || sessionId.isEmpty()) {
            return;
        }
        String day = dayName(at);
        String time = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date(at));
        String who = "user".equals(role) ? "\u6211" : herName(ctx);
        String body = content.trim();

        File dir = new File(Session.dir(ctx, sessionId), DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File f = new File(dir, ALL_FILE);
        boolean fresh = !f.exists();

        StringBuilder sb = new StringBuilder();
        if (fresh) {
            sb.append("# \u804A\u5929\u8BB0\u5F55\n\n");
            sb.append("## ").append(day).append("\n\n");
        } else {
            String lastDay = lastDayIn(f);
            if (!day.equals(lastDay)) {
                sb.append("## ").append(day).append("\n\n");
            }
        }

        sb.append("**").append(time).append(" ").append(who).append("**\uFF1A");
        if (body.contains("\n")) {
            sb.append("\n").append(body);
        } else {
            sb.append(body);
        }
        sb.append("\n\n");

        FileOutputStream out = new FileOutputStream(f, true);
        try {
            out.write(sb.toString().getBytes("UTF-8"));
            out.flush();
        } finally {
            try {
                out.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /** 读文件里最后一个 "## 日期" 标记。 */
    private static String lastDayIn(File f) {
        try {
            String txt = read(f);
            if (txt == null) {
                return "";
            }
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?m)^##\\s*(\\d{4}-\\d{2}-\\d{2})").matcher(txt);
            String last = "";
            while (m.find()) {
                last = m.group(1);
            }
            return last;
        } catch (Throwable t) {
            return "";
        }
    }

    /** 聊天记录那个文件。 */
    public static File today() {
        File d = root();
        return d == null ? null : new File(d, ALL_FILE);
    }

    /** 列出所有存档日（新→旧）。 */
    public static java.util.List<File> days() {
        java.util.List<File> out = new java.util.ArrayList<File>();
        try {
            File d = root();
            if (d == null) {
                return out;
            }
            File[] fs = d.listFiles();
            if (fs == null) {
                return out;
            }
            for (File f : fs) {
                if (f.isFile() && f.getName().endsWith(".md")) {
                    out.add(f);
                }
            }
            java.util.Collections.sort(out, new java.util.Comparator<File>() {
                @Override
                public int compare(File a, File b) {
                    return b.getName().compareTo(a.getName());
                }
            });
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 读某一天的全文。 */
    public static String read(File f) {
        try {
            return Memory.readFile(f);
        } catch (Throwable t) {
            return "";
        }
    }

    /** 在所有存档里搜（返回匹配行）。 */
    public static java.util.List<String> search(String q) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        if (q == null || q.trim().isEmpty()) {
            return out;
        }
        String key = q.trim().toLowerCase(Locale.US);
        for (File f : days()) {
            try {
                String txt = read(f);
                String day = f.getName().replace(".md", "");
                for (String line : txt.split("\n")) {
                    if (line.toLowerCase(Locale.US).contains(key)) {
                        out.add(day + "  " + line.trim());
                        if (out.size() >= 300) {
                            return out;
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return out;
    }

    // ---------- 写 ----------

    private static void write(Context ctx, String name, String text, boolean append)
            throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            writeMediaStore(ctx, name, text, append);
            return;
        }
        File dir = root();
        if (dir == null) {
            return;
        }
        File f = new File(dir, name);
        FileOutputStream o = new FileOutputStream(f, append);
        try {
            o.write(text.getBytes("UTF-8"));
            o.flush();
        } finally {
            try {
                o.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void writeMediaStore(Context ctx, String name, String text, boolean append)
            throws Exception {
        // 只写 App 私有目录，不碰 Documents，也不建 md 文件供外部查看
        File dir = root();
        if (dir == null) {
            return;
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File f = new File(dir, name);
        java.io.FileOutputStream o = new java.io.FileOutputStream(f, append);
        try {
            o.write(text.getBytes("UTF-8"));
            o.flush();
        } finally {
            try {
                o.close();
            } catch (Throwable ignored) {
            }
        }
    }



    private static String herName(Context c) {
        try {
            String n = c.getSharedPreferences(MainActivity.PREF, Context.MODE_PRIVATE)
                    .getString("her_name", "");
            if (n != null && !n.trim().isEmpty()) {
                return n;
            }
        } catch (Throwable ignored) {
        }
        return "大肥鱼";
    }

    /** 在 App 里打开聊天记录页：列出所有存档日，点开看全文。 */
    /**
     * 聊天记录：直接按聊天气泡的样子翻，不是让你去看 md。
     *
     * 数据来自 history（内存）+ 存档文件（磁盘），两边合起来按时间排。
     */
    /**
     * 历史聊天记录：一整页列出所有聊天，点一条看它的记录。
     * 长按可以改标题。
     */
    public static void open(final android.app.Activity act, final Chat chat) {
        try {
            new Thread(() -> {
                try {
                    mergeDayFiles();
                } catch (Throwable ignored) {
                }
            }).start();

            showList(act, chat, false);
        } catch (Throwable t) {
            SimpleDialog.alert(act, "历史聊天记录", "打开失败：" + t);
        }
    }

    /** 画「历史聊天记录」列表页。 */
    private static void showList(final android.app.Activity act, final Chat chat,
                                 final boolean selectMode) {
        final java.util.List<Session.Item> sessions = Session.list(act);

        // 统计每个聊天有多少条、最后时间
        final java.util.Map<String, Integer> counts = new java.util.HashMap<String, Integer>();
        final java.util.Map<String, Long> lastAt = new java.util.HashMap<String, Long>();
        final java.util.List<Msg> all = loadAll(act, chat);
        for (Msg m : all) {
            if (m.sessionId == null) {
                continue;
            }
            Integer c = counts.get(m.sessionId);
            counts.put(m.sessionId, c == null ? 1 : c + 1);
            Long la = lastAt.get(m.sessionId);
            if (la == null || m.at > la) {
                lastAt.put(m.sessionId, m.at);
            }
        }

        final android.app.Dialog dlg = new android.app.Dialog(act,
                android.R.style.Theme_DeviceDefault_NoActionBar);
        try {
            dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Theme.bg()));
        } catch (Throwable ignored) {
        }

        final android.widget.FrameLayout root = new android.widget.FrameLayout(act);
        // 用聊天背景（列表也可以用自己单独设的）
        try {
            android.graphics.drawable.Drawable bgD = chatBg(act);
            if (bgD != null) {
                root.setBackground(bgD);
            } else {
                root.setBackgroundColor(Theme.bg());
            }
        } catch (Throwable t) {
            root.setBackgroundColor(Theme.bg());
        }

        final android.widget.ScrollView sv = new android.widget.ScrollView(act);
        sv.setFillViewport(true);
        final android.widget.LinearLayout list = new android.widget.LinearLayout(act);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dp(act, 14);
        list.setPadding(pad, dp(act, 64), pad, dp(act, 30));

        // 空提示
        if (sessions.isEmpty()) {
            android.widget.TextView t = new android.widget.TextView(act);
            t.setText("还没有聊过天。");
            t.setTextSize(13);
            t.setTextColor(Theme.muted());
            t.setPadding(dp(act, 8), dp(act, 20), dp(act, 8), dp(act, 8));
            list.addView(t);
        }

        for (int k = 0; k < sessions.size(); k++) {
            final Session.Item it = sessions.get(k);
            final int cnt = counts.get(it.id) == null ? 0 : counts.get(it.id);
            final Long la = lastAt.get(it.id);

            list.addView(archiveRow(act, it, cnt, la, () -> {
                // 点进去看这个聊天的记录
                if (cnt == 0) {
                    SimpleDialog.alert(act, it.title, "这个聊天没有记录。");
                    return;
                }
                java.util.List<Msg> one = new java.util.ArrayList<Msg>();
                for (Msg m : all) {
                    if (it.id.equals(m.sessionId)) {
                        one.add(m);
                    }
                }
                showBubbles(act, one, it.title);
            }, () -> {
                // 长按改标题
                MDialog.input(act, "改标题", "给这个聊天起个名字", it.title, text -> {
                    try {
                        String t = text == null ? "" : text.trim();
                        if (t.isEmpty()) {
                            return;
                        }
                        Session.rename(act, it.id, t);
                        dlg.dismiss();
                        android.view.View dv = act.getWindow().getDecorView();
                        dv.postDelayed(() -> showList(act, chat, false), 150);
                    } catch (Throwable ignored) {
                    }
                }).show();
            }));
        }

        sv.addView(list);
        root.addView(sv, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));

        // 顶栏
        android.widget.LinearLayout bar = new android.widget.LinearLayout(act);
        bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setPadding(pad, dp(act, 12), pad, dp(act, 12));
        bar.setBackgroundColor(Theme.bar());

        android.widget.TextView title = new android.widget.TextView(act);
        title.setText("历史聊天记录  ·  " + sessions.size() + " 个");
        title.setTextSize(16);
        title.setTextColor(Theme.text());
        bar.addView(title, new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.TextView close = new android.widget.TextView(act);
        close.setText("关闭");
        close.setTextSize(14);
        close.setTextColor(Theme.dark ? 0xFF6FB8FF : 0xFF1A73E8);
        close.setPadding(dp(act, 12), dp(act, 6), dp(act, 4), dp(act, 6));
        close.setClickable(true);
        close.setOnClickListener(v -> dlg.dismiss());
        bar.addView(close);

        android.widget.FrameLayout.LayoutParams blp =
                new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = android.view.Gravity.TOP;
        root.addView(bar, blp);

        dlg.setContentView(root);
        dlg.setOnShowListener(x -> {
            try {
                dlg.getWindow().setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            } catch (Throwable ignored) {
            }
        });
        dlg.show();

        try {
            FontKit.applyTo(act, root);
        } catch (Throwable ignored) {
        }
    }

    /** 列表里的一行。 */
    private static android.view.View archiveRow(final android.app.Activity act,
                                                final Session.Item it, int cnt, Long lastAt,
                                                final Runnable onTap,
                                                final Runnable onLongPress) {
        android.widget.LinearLayout row = new android.widget.LinearLayout(act);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(dp(act, 14), dp(act, 13), dp(act, 14), dp(act, 13));

        android.graphics.drawable.GradientDrawable g =
                new android.graphics.drawable.GradientDrawable();
        g.setCornerRadius(dp(act, 14));
        // 半透明卡片，压在背景图上也能看清
        g.setColor(Theme.dark ? 0xE622262D : 0xF2FFFFFF);
        if (Theme.dark) {
            g.setStroke(dp(act, 1), 0x22FFFFFF);
        }
        row.setBackground(g);

        android.widget.LinearLayout texts = new android.widget.LinearLayout(act);
        texts.setOrientation(android.widget.LinearLayout.VERTICAL);
        row.addView(texts, new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.TextView t = new android.widget.TextView(act);
        t.setText(it.title);
        t.setTextSize(15.5f);
        t.setTextColor(Theme.text());
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        texts.addView(t);

        String when = lastAt == null || lastAt <= 0 ? ""
                : new java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
                .format(new java.util.Date(lastAt));
        android.widget.TextView sub = new android.widget.TextView(act);
        sub.setText(cnt + " \u6761" + (when.isEmpty() ? "" : "  \u00B7  " + when));
        sub.setTextSize(11.5f);
        sub.setTextColor(Theme.muted());
        sub.setPadding(0, dp(act, 3), 0, 0);
        texts.addView(sub);

        // 右箭头
        android.widget.TextView arrow = new android.widget.TextView(act);
        arrow.setText("\u203A");
        arrow.setTextSize(20);
        arrow.setTextColor(Theme.muted());
        arrow.setPadding(dp(act, 8), 0, 0, 0);
        row.addView(arrow);

        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (onTap != null) {
                onTap.run();
            }
        });
        row.setLongClickable(true);
        row.setOnLongClickListener(v -> {
            if (onLongPress != null) {
                onLongPress.run();
            }
            return true;
        });

        android.widget.LinearLayout.LayoutParams lp =
                new android.widget.LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(act, 8);
        row.setLayoutParams(lp);
        return row;
    }

    /** 一条消息。 */
    private static final class Msg {
        String who;
        String text;
        long at;
        boolean mine;
        String sessionId;      // 属于哪个聊天
        String sessionTitle;   // 那个聊天的标题
    }

    /**
     * 把所有消息读出来：先读磁盘存档，再用内存历史补最近的。
     * 按时间排序，去重。
     */
    /**
     * 读出所有会话的全部消息。
     * 不管当前停在哪个聊天，历史聊天记录里都能看到全部。
     */
    private static java.util.List<Msg> loadAll(android.app.Activity act, Chat chat) {
        java.util.List<Msg> out = new java.util.ArrayList<Msg>();

        java.util.List<Session.Item> sessions = Session.list(act);
        String cur = Session.current(act);

        // 1. 逐个会话读磁盘上的存档
        for (Session.Item it : sessions) {
            java.util.List<Msg> part = new java.util.ArrayList<Msg>();
            try {
                // writeTo() 写的是 sessions/<id>/大肥鱼聊天记录/聊天记录.md
                File d = rootOf(act, it.id);
                File f = new File(d, ALL_FILE);
                if (f.exists()) {
                    String txt = read(f);
                    if (txt != null) {
                        parseMd(txt, act, part);
                    }
                }
                // 兼容：目录下其它 md（老格式一天一个文件）
                File[] fs = d.listFiles();
                if (fs != null) {
                    for (File ff : fs) {
                        if (ff.isFile() && ff.getName().endsWith(".md")
                                && !ff.getName().equals(ALL_FILE)) {
                            String t2 = read(ff);
                            if (t2 != null) {
                                parseMd(t2, act, part);
                            }
                        }
                    }
                }
                // 再兼容一层：sessions/<id>/聊天记录.md（早期写到这一层）
                File legacy = Session.sessionFile(act, it.id, ALL_FILE);
                if (legacy != null && legacy.exists() && !legacy.getAbsolutePath()
                        .equals(f.getAbsolutePath())) {
                    String t3 = read(legacy);
                    if (t3 != null) {
                        parseMd(t3, act, part);
                    }
                }
            } catch (Throwable ignored) {
            }
            for (Msg m : part) {
                m.sessionId = it.id;
                m.sessionTitle = it.title;
                out.add(m);
            }
        }

        // 2. 当前会话还有内存里的（可能比磁盘新）
        try {
            if (chat != null) {
                String sid = chat.sessionId();
                String title = cur;
                for (Session.Item it : sessions) {
                    if (it.id.equals(sid)) {
                        title = it.title;
                        break;
                    }
                }
                org.json.JSONArray h = chat.history();
                for (int k = 0; k < h.length(); k++) {
                    org.json.JSONObject m = h.getJSONObject(k);
                    Msg msg = new Msg();
                    msg.mine = "user".equals(m.optString("role"));
                    msg.who = msg.mine ? "我" : herName(act);
                    msg.text = m.optString("content", "");
                    msg.at = m.optLong("at", 0);
                    msg.sessionId = sid;
                    msg.sessionTitle = title;
                    if (!msg.text.isEmpty()) {
                        out.add(msg);
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 3. 排序（没有时间的放最后）
        java.util.Collections.sort(out, new java.util.Comparator<Msg>() {
            @Override
            public int compare(Msg a, Msg b) {
                if (a.at > 0 && b.at > 0) {
                    return Long.compare(a.at, b.at);
                }
                if (a.at > 0) return -1;
                if (b.at > 0) return 1;
                return 0;
            }
        });

        // 4. 去重
        java.util.List<Msg> uniq = new java.util.ArrayList<Msg>();
        java.util.Set<String> seen = new java.util.HashSet<String>();
        for (Msg m : out) {
            String key = (m.sessionId == null ? "" : m.sessionId) + "|"
                    + m.mine + "|" + m.text + "|" + (m.at / 1000);
            if (seen.add(key)) {
                uniq.add(m);
            }
        }
        return uniq;
    }

    /** 从 md 里解析出消息行：**15:35:20 我**：内容 */
    private static void parseMd(String txt, android.app.Activity act, java.util.List<Msg> out) {
        try {
            String[] lines = txt.split("\n");
            Msg cur = null;
            for (String raw : lines) {
                String line = raw.trim();
                int m1 = line.indexOf("**：");
                if (line.startsWith("**") && m1 > 0) {
                    // 新消息开始
                    if (cur != null && !cur.text.isEmpty()) {
                        out.add(cur);
                    }
                    cur = new Msg();
                    String head = line.substring(2, m1);   // 15:35:20 我
                    int sp = head.indexOf(' ');
                    String timeStr = sp > 0 ? head.substring(0, sp) : "";
                    String who = sp > 0 ? head.substring(sp + 1) : head;
                    cur.who = who;
                    cur.mine = "我".equals(who);
                    cur.at = parseTime(timeStr);
                    cur.text = line.substring(m1 + 3).trim();
                } else if (cur != null && !line.isEmpty()
                        && !line.startsWith("#") && !line.startsWith("##")) {
                    // 多行内容的续行
                    if (!cur.text.isEmpty()) {
                        cur.text = cur.text + "\n";
                    }
                    cur.text = cur.text + line;
                }
            }
            if (cur != null && !cur.text.isEmpty()) {
                out.add(cur);
            }
        } catch (Throwable ignored) {
        }
    }

    private static long parseTime(String hhmmss) {
        try {
            String[] p = hhmmss.split(":");
            if (p.length < 2) {
                return 0;
            }
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.set(java.util.Calendar.HOUR_OF_DAY, Integer.parseInt(p[0]));
            c.set(java.util.Calendar.MINUTE, Integer.parseInt(p[1]));
            c.set(java.util.Calendar.SECOND, p.length > 2 ? Integer.parseInt(p[2]) : 0);
            return c.getTimeInMillis();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 用气泡把聊天记录画出来（和主界面一个样子）。 */
    private static void showBubbles(final android.app.Activity act, final java.util.List<Msg> msgs,
                                    final String sessionTitle) {
        final android.app.Dialog dlg = new android.app.Dialog(act,
                android.R.style.Theme_DeviceDefault_NoActionBar);
        try {
            dlg.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(Theme.bg()));
        } catch (Throwable ignored) {
        }

        final android.widget.FrameLayout root = new android.widget.FrameLayout(act);
        // 用和主界面一样的聊天背景
        try {
            android.graphics.drawable.Drawable bgD = chatBg(act);
            if (bgD != null) {
                root.setBackground(bgD);
            } else {
                root.setBackgroundColor(Theme.bg());
            }
        } catch (Throwable t) {
            root.setBackgroundColor(Theme.bg());
        }

        final android.widget.ScrollView sv = new android.widget.ScrollView(act);
        sv.setFillViewport(true);

        final android.widget.LinearLayout list = new android.widget.LinearLayout(act);
        list.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = dp(act, 12);
        // 顶部要躲开悬浮的标题栏（约 52dp），否则第一条消息会被压在上面
        list.setPadding(pad, dp(act, 60), pad, pad);

        // 顶部标题栏
        android.widget.LinearLayout bar = new android.widget.LinearLayout(act);
        bar.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        bar.setGravity(android.view.Gravity.CENTER_VERTICAL);
        bar.setPadding(pad, dp(act, 10), pad, dp(act, 10));
        bar.setBackgroundColor(Theme.bar());

        android.widget.TextView title = new android.widget.TextView(act);
        title.setText((sessionTitle == null ? "聊天记录" : sessionTitle)
                + "  ·  " + msgs.size() + " 条");
        title.setTextSize(15);
        title.setTextColor(Theme.text());
        android.widget.LinearLayout.LayoutParams tlp = new android.widget.LinearLayout.LayoutParams(
                0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(title, tlp);

        android.widget.TextView export = new android.widget.TextView(act);
        export.setText("导出");
        export.setTextSize(14);
        export.setTextColor(Theme.imMine());
        export.setPadding(dp(act, 10), dp(act, 6), dp(act, 10), dp(act, 6));
        export.setOnClickListener(v -> exportText(act, msgs));
        bar.addView(export);

        android.widget.TextView close = new android.widget.TextView(act);
        close.setText("关闭");
        close.setTextSize(14);
        close.setTextColor(Theme.imMine());
        close.setPadding(dp(act, 10), dp(act, 6), dp(act, 10), dp(act, 6));
        close.setOnClickListener(v -> dlg.dismiss());
        bar.addView(close);

        // 气泡
        String lastDay = "";
        String lastSession = null;
        for (Msg m : msgs) {
            // 换会话时插一个标题，区分不同聊天
            if (m.sessionId != null && !m.sessionId.equals(lastSession)) {
                lastSession = m.sessionId;
                android.widget.TextView st = new android.widget.TextView(act);
                st.setText("\u3010" + (m.sessionTitle == null ? "\u804A\u5929" : m.sessionTitle) + "\u3011");
                st.setTextSize(11.5f);
                st.setTextColor(Theme.dark ? 0xFF8FB8E8 : 0xFF5A6470);
                st.setGravity(android.view.Gravity.CENTER);
                st.setPadding(0, dp(act, 16), 0, dp(act, 6));
                list.addView(st);
                try {
                    FontKit.r(act, st);
                } catch (Throwable ignored) {
                }
                // 会话换了一定也要打日期
                lastDay = "";
            }

            String day = "";
            if (m.at > 0) {
                day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(m.at));
            }
            if (!day.isEmpty() && !day.equals(lastDay)) {
                lastDay = day;

                // 日期隔断：带小胶囊底，字加深，不然压在背景图上几乎看不见
                android.widget.LinearLayout drow = new android.widget.LinearLayout(act);
                drow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
                drow.setGravity(android.view.Gravity.CENTER);
                drow.setPadding(0, dp(act, 14), 0, dp(act, 8));

                android.widget.TextView d = new android.widget.TextView(act);
                d.setText(day);
                d.setTextSize(11.5f);
                d.setTextColor(Theme.dark ? 0xFFE8F0FA : 0xFF2A2F36);
                d.setGravity(android.view.Gravity.CENTER);
                d.setPadding(dp(act, 10), dp(act, 3), dp(act, 10), dp(act, 3));

                android.graphics.drawable.GradientDrawable dg =
                        new android.graphics.drawable.GradientDrawable();
                dg.setCornerRadius(dp(act, 10));
                dg.setColor(Theme.dark ? 0x99000000 : 0xCCFFFFFF);
                if (Theme.dark) {
                    dg.setStroke(dp(act, 1), 0x33FFFFFF);
                }
                d.setBackground(dg);
                drow.addView(d);

                list.addView(drow);
            }
            // 她一条回复里通常有好几行，主界面是一行一个气泡，
            // 这里也拆开，不然看着像一大段
            String[] parts = (m.text == null ? "" : m.text).split("\n");
            if (parts.length > 1) {
                for (String part : parts) {
                    String one = part.trim();
                    if (one.isEmpty()) {
                        continue;
                    }
                    Msg sub = new Msg();
                    sub.who = m.who;
                    sub.mine = m.mine;
                    sub.at = m.at;
                    sub.text = one;
                    list.addView(bubble(act, sub));
                }
            } else {
                list.addView(bubble(act, m));
            }
        }

        sv.addView(list);
        root.addView(sv, new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT));
        android.widget.FrameLayout.LayoutParams blp = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = android.view.Gravity.TOP;
        root.addView(bar, blp);

        dlg.setContentView(root);
        dlg.setOnShowListener(x -> {
            try {
                dlg.getWindow().setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT);
            } catch (Throwable ignored) {
            }
        });
        dlg.show();
    }

    private static int dp(android.content.Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 一个气泡 + 头像，跟主界面一样。 */
    private static android.view.View bubble(final android.app.Activity act, final Msg m) {
        int av = dp(act, 30);
        int gap = dp(act, 6);

        // 外层：头像 + 气泡
        android.widget.LinearLayout row = new android.widget.LinearLayout(act);
        row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.TOP);
        row.setPadding(0, dp(act, 6), 0, dp(act, 6));

        // 气泡（带时间）
        android.widget.LinearLayout box = new android.widget.LinearLayout(act);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);

        // 先看是不是表情标记：[[stickers/xx.png]] 或 ${xx}
        String sticker = stickerAsset(act, m.text);
        android.view.View content;

        if (sticker != null) {
            // 画图，不画字
            GifView gv = new GifView(act);
            gv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            int sz = dp(act, 120);
            content = gv;
            loadSticker(act, gv, sticker);
            android.widget.LinearLayout.LayoutParams glp =
                    new android.widget.LinearLayout.LayoutParams(sz, sz);
            box.addView(content, glp);
        } else {
            android.widget.TextView tv = new android.widget.TextView(act);
            tv.setText(m.text);
            tv.setTextSize(15);
            tv.setTextColor(m.mine ? Theme.archiveMineText() : Theme.imText());
            tv.setLineSpacing(0f, 1.28f);

            android.graphics.drawable.GradientDrawable g =
                    new android.graphics.drawable.GradientDrawable();
            g.setCornerRadius(dp(act, 17));
            g.setColor(m.mine ? Theme.archiveMine() : Theme.imTheirs());
            tv.setBackground(g);
            int hp = dp(act, 13), vp = dp(act, 9);
            tv.setPadding(hp, vp, hp, vp);
            content = tv;
            box.addView(content);
            try {
                FontKit.r(act, tv);
            } catch (Throwable ignored) {
            }
        }


        android.widget.ImageView av1 = avatar(act, m.mine);
        android.widget.Space sp = new android.widget.Space(act);
        android.widget.LinearLayout.LayoutParams gapLp = new android.widget.LinearLayout.LayoutParams(gap, 1);

        if (m.mine) {
            // 你的：气泡靠右，头像在最右
            android.widget.Space left = new android.widget.Space(act);
            row.addView(left, new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
            row.addView(box, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(sp, gapLp);
            row.addView(av1, new android.widget.LinearLayout.LayoutParams(av, av));
        } else {
            // 她的：头像在最左，气泡靠左
            row.addView(av1, new android.widget.LinearLayout.LayoutParams(av, av));
            row.addView(sp, gapLp);
            row.addView(box, new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));
            android.widget.Space right = new android.widget.Space(act);
            row.addView(right, new android.widget.LinearLayout.LayoutParams(0, 1, 1f));
        }
        return row;
    }

    /** 头像（圆形）。 */
    private static android.widget.ImageView avatar(android.app.Activity act, boolean mine) {
        android.widget.ImageView iv = new android.widget.ImageView(act);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        try {
            android.graphics.Bitmap b = mine
                    ? AvatarCache.getMine(act)
                    : AvatarCache.get(act);
            if (b != null) {
                iv.setImageDrawable(new CircleImageDrawable(b));
            } else {
                android.graphics.drawable.GradientDrawable g =
                        new android.graphics.drawable.GradientDrawable();
                g.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                g.setColor(Theme.dark ? 0xFF2A3340 : 0xFFDCEAF5);
                iv.setBackground(g);
            }
        } catch (Throwable ignored) {
        }
        return iv;
    }



    private static String oneLineContent(String s) {
        if (s == null) return "";
        if (s.startsWith("${")) return "[表情]";
        String t = s.replace("\n", " ").trim();
        return t;
    }



    /**
     * 把「2026-11-19 (1).md」「2026-11-19 (2).md」这种碎片合并成一个。
     *
     * MediaStore 之前查不到已有文件，每说一句就 insert 一个新文件，
     * 结果同一天生成了一堆。这里把它们按时间顺序拼起来，删掉碎片。
     *
     * @return 合并了几个
     */
    /**
     * 把所有 .md 合成一个「聊天记录.md」。
     *
     * 之前每天一个文件，还因为 MediaStore 的 bug 生成了一堆碎片。
     * 现在统一并到一个文件里，按日期分章节。
     *
     * @return 合并了几个文件
     */
    public static int mergeDayFiles() {
        int count = 0;
        try {
            File sub = root();
            File docs = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOCUMENTS);

            java.util.List<File> all = new java.util.ArrayList<File>();
            collectMd(sub, all);
            if (docs != null && !docs.equals(sub)) {
                collectMd(docs, all);
            }
            // 排除合并目标本身
            for (int k = all.size() - 1; k >= 0; k--) {
                if (all.get(k).getName().equals(ALL_FILE)) {
                    all.remove(k);
                }
            }
            if (all.isEmpty()) {
                return 0;
            }

            File targetDir = (sub != null && sub.exists()) ? sub : docs;
            if (targetDir == null) {
                return 0;
            }

            // 按日期分组
            java.util.Map<String, java.util.List<File>> byDay =
                    new java.util.TreeMap<String, java.util.List<File>>();
            java.util.regex.Pattern pat =
                    java.util.regex.Pattern.compile("^(\\d{4}-\\d{2}-\\d{2})");
            java.util.List<File> unknown = new java.util.ArrayList<File>();

            for (File f : all) {
                java.util.regex.Matcher m = pat.matcher(f.getName());
                if (!m.find()) {
                    unknown.add(f);
                    continue;
                }
                String day = m.group(1);
                java.util.List<File> list = byDay.get(day);
                if (list == null) {
                    list = new java.util.ArrayList<File>();
                    byDay.put(day, list);
                }
                list.add(f);
            }

            // 拼：
            // # 聊天记录
            // ## 2026-11-19
            // **15:35:20 我**：xxx
            StringBuilder sb = new StringBuilder();
            sb.append("# 聊天记录\n\n");

            for (java.util.Map.Entry<String, java.util.List<File>> e : byDay.entrySet()) {
                java.util.List<File> list = e.getValue();
                final java.util.Map<File, Long> stamp = new java.util.HashMap<File, Long>();
                for (File f : list) {
                    stamp.put(f, f.lastModified());
                }
                java.util.Collections.sort(list, new java.util.Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        return Long.compare(stamp.get(a), stamp.get(b));
                    }
                });

                sb.append("## ").append(e.getKey()).append("\n\n");
                for (File f : list) {
                    String txt = read(f);
                    if (txt == null) {
                        continue;
                    }
                    String body = txt
                            .replaceAll("(?m)^#\\s*\\d{4}-\\d{2}-\\d{2}\\s*$", "")
                            .trim();
                    if (!body.isEmpty()) {
                        sb.append(body).append("\n\n");
                    }
                }
            }

            for (File f : unknown) {
                String txt = read(f);
                if (txt != null && !txt.trim().isEmpty()) {
                    sb.append("## ").append(f.getName().replace(".md", "")).append("\n\n");
                    sb.append(txt.trim()).append("\n\n");
                }
            }

            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            File target = new File(targetDir, ALL_FILE);
            java.io.FileOutputStream o = new java.io.FileOutputStream(target, false);
            try {
                o.write(sb.toString().getBytes("UTF-8"));
                o.flush();
            } finally {
                try {
                    o.close();
                } catch (Throwable ignored) {
                }
            }

            // 删掉所有碎文件
            for (File f : all) {
                try {
                    if (f.delete()) {
                        count++;
                    }
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return count;
    }

    /** 收集目录下的 .md 文件。 */
    private static void collectMd(File dir, java.util.List<File> out) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] fs = dir.listFiles();
        if (fs == null) {
            return;
        }
        for (File f : fs) {
            if (f.isFile() && f.getName().endsWith(".md")) {
                out.add(f);
            }
        }
    }


    /**
     * 把聊天记录导出成一个 txt，存到 Download 里。
     *
     * 聊天数据本身在 App 私有目录，卸载就没了；
     * 这个 txt 是给你自己留着、能带走的纯文本。
     */
    private static void exportText(android.app.Activity act, final java.util.List<Msg> msgs) {
        if (msgs == null || msgs.isEmpty()) {
            SimpleDialog.alert(act, "导出", "没有可导出的内容。");
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("大肥鱼聊天记录\n");
        sb.append("导出时间：").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date())).append("\n");
        sb.append("共 ").append(msgs.size()).append(" 条\n");
        sb.append("────────────────────\n\n");

        String lastDay = "";
        for (Msg m : msgs) {
            String day = "";
            if (m.at > 0) {
                day = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date(m.at));
            }
            if (!day.isEmpty() && !day.equals(lastDay)) {
                lastDay = day;
                sb.append("\n【").append(day).append("】\n\n");
            }
            if (m.at > 0) {
                sb.append("[").append(new SimpleDateFormat("HH:mm:ss", Locale.US)
                        .format(new Date(m.at))).append("] ");
            }
            sb.append(m.mine ? "我" : m.who).append("：").append(m.text).append("\n");
        }

        final String content = sb.toString();
        final String fileName = "大肥鱼聊天记录_"
                + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date())
                + ".txt";

        // Android 10+ 用 MediaStore 写进 Download；老版本走文件
        new Thread(() -> {
            String err = null;
            try {
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentValues cv = new android.content.ContentValues();
                    cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                    cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                    cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                            android.os.Environment.DIRECTORY_DOWNLOADS);
                    android.net.Uri uri = act.getContentResolver().insert(
                            android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                    if (uri == null) {
                        err = "创建文件失败";
                    } else {
                        java.io.OutputStream o = act.getContentResolver().openOutputStream(uri);
                        if (o == null) {
                            err = "打开文件失败";
                        } else {
                            try {
                                o.write(content.getBytes("UTF-8"));
                                o.flush();
                            } finally {
                                try {
                                    o.close();
                                } catch (Throwable ignored) {
                                }
                            }
                        }
                    }
                } else {
                    java.io.File d = android.os.Environment
                            .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
                    if (d != null && !d.exists()) {
                        d.mkdirs();
                    }
                    java.io.File f = new java.io.File(d, fileName);
                    java.io.FileOutputStream o = new java.io.FileOutputStream(f);
                    try {
                        o.write(content.getBytes("UTF-8"));
                        o.flush();
                    } finally {
                        try {
                            o.close();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            } catch (Throwable t) {
                err = t.getClass().getSimpleName() + ": " + t.getMessage();
            }

            final String e = err;
            act.runOnUiThread(() -> {
                if (e == null) {
                    SimpleDialog.alert(act, "导出成功",
                            "已保存到 Download/" + fileName + "\n\n"
                                    + "共 " + msgs.size() + " 条消息。");
                } else {
                    SimpleDialog.alert(act, "导出失败", e);
                }
            });
        }).start();
    }

    /**
     * 聊天背景（和主界面一致的：先看内置预设，再看自己设的 bg.png）。
     */
    private static android.graphics.drawable.Drawable chatBg(android.app.Activity act) {
        try {
            android.content.SharedPreferences p = act.getSharedPreferences(
                    MainActivity.PREF, android.content.Context.MODE_PRIVATE);

            // 0. 历史记录专用的（如果设过）
            java.io.File ab = new java.io.File(act.getFilesDir(), "archive_bg.png");
            if (ab.exists()) {
                android.graphics.Bitmap b0 = android.graphics.BitmapFactory
                        .decodeFile(ab.getAbsolutePath());
                if (b0 != null) {
                    return new BgDrawable(b0);
                }
            }

            // 1. 内置预设
            String preset = p.getString("chat_bg_preset", "");
            if (preset != null && !preset.isEmpty()) {
                int rid = BgPicker.drawableId(act, preset);
                if (rid != 0) {
                    android.graphics.BitmapFactory.Options o =
                            new android.graphics.BitmapFactory.Options();
                    android.graphics.Bitmap b = android.graphics.BitmapFactory.decodeResource(
                            act.getResources(), rid, o);
                    if (b != null) {
                        return new BgDrawable(b);
                    }
                }
            }

            // 2. 自己的 bg.png
            java.io.File f = new java.io.File(act.getFilesDir(), "bg.png");
            if (f.exists()) {
                android.graphics.Bitmap b = android.graphics.BitmapFactory
                        .decodeFile(f.getAbsolutePath());
                if (b != null) {
                    return new BgDrawable(b);
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** 把 [[stickers/xx.png]] 或 ${xx} 解析成 assets 路径；不是表情返回 null。 */
    private static String stickerAsset(android.app.Activity act, String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        String inner = null;
        if (t.startsWith("[[") && t.endsWith("]]")) {
            inner = t.substring(2, t.length() - 2).trim();
        } else if (t.startsWith("${") && t.endsWith("}")) {
            inner = t.substring(2, t.length() - 1).trim();
        }
        if (inner == null || inner.isEmpty()) {
            return null;
        }
        if (inner.startsWith("user/") || inner.startsWith("stickers/")) {
            return inner;
        }
        // 裸名字：试着补扩展名
        for (String ext : new String[]{"", ".png", ".gif", ".jpg", ".webp"}) {
            String cand = "stickers/" + inner + ext;
            try {
                android.content.res.AssetManager am = act.getAssets();
                String dir = "stickers";
                String[] all = am.list(dir);
                for (String n : all) {
                    if (("stickers/" + n).equals(cand) || n.equals(inner + ext)) {
                        return "stickers/" + n;
                    }
                }
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    /** 读表情图到 GifView。 */
    private static void loadSticker(android.app.Activity act, GifView gv, String asset) {
        try {
            if (asset.startsWith("user/")) {
                java.io.File f = new java.io.File(
                        StickerPanel.userDir(act), asset.substring(5));
                if (f.isFile()) {
                    gv.setSource(f);
                    return;
                }
            } else {
                java.io.InputStream in = act.getAssets().open(asset);
                java.io.File tmp = new java.io.File(act.getCacheDir(),
                        "arc_" + Math.abs(asset.hashCode()) + ".img");
                java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                out.flush();
                out.close();
                in.close();
                gv.setSource(tmp);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 切到指定会话（之后 append 都写到它里面）。 */
    public static synchronized void useSession(android.content.Context c, String id) {
        if (c != null) {
            appCtx = c.getApplicationContext();
        }
        curSession = id;
    }
}
