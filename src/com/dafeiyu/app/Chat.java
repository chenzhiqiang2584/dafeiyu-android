package com.dafeiyu.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Calendar;

/**
 * 对话组装 + 历史存储。
 *
 * 负责三件事：
 *   1. 从 assets 读人格，拼出 system prompt
 *   2. 从 Memory 取这一轮相关的记忆，塞进 prompt
 *   3. 把聊天历史存本地 JSON
 */
public class Chat {

    private final Context ctx;
    private final Memory memory;
    private final String sessionId;
    private final File historyFile;
    private final File summaryFile;

    private JSONArray history = new JSONArray();
    private String persona = "";

    private static final int KEEP_TURNS = 20;     // 带进 prompt 的最近轮数
    private static final int MAX_HISTORY = 500;   // 本地最多存多少条

    // ---- 上下文压缩 ----
    public static final int AUTO_COMPRESS_AT = 30;   // 攒够多少条就自动压一次
    private static final int SUMMARY_MAX_CHARS = 1600; // 摘要太长就再压一遍

    /**
     * 字符预算触发。
     *
     * 原来只看条数：30 条一压。可她有时候一回合说几百字，
     * 30 条就能到上万 token；有时候又全是「嗯」，30 条才几百字。
     * 按条数触发要么压太勤要么压太晚，所以同时看字数。
     */
    public static final int CHAR_BUDGET = 3000;

    /** 窗口里最多带多少条（防止条数失控）。 */
    private static final int WINDOW_MSGS = 60;
    private String summary = "";                     // 早期对话的摘要

    public Chat(Context c) {
        this(c, null);
    }

    /**
     * @param sessionId 指定会话；null 表示用当前会话
     */
    public Chat(Context c, String sessionId) {
        ctx = c.getApplicationContext();
        memory = new Memory(c);
        // 每个会话独立的 history.json / summary.md
        String sid = sessionId;
        if (sid == null || sid.isEmpty()) {
            sid = Session.current(c.getApplicationContext());
        }
        this.sessionId = sid;
        File d = Session.dir(ctx, sid);
        if (!d.exists()) {
            d.mkdirs();
        }
        historyFile = new File(d, "history.json");
        summaryFile = new File(d, "summary.md");
        loadPersona();
        loadHistory();
        loadSummary();
    }

    /** 这个 Chat 属于哪个会话。 */
    public String sessionId() {
        return sessionId;
    }

    public Memory memory() { return memory; }

    // ---------- 人格 ----------
    private void loadPersona() {
        // 用户改过的人格优先（filesDir/persona.md），没有才用内置那份
        try {
            File f = new File(ctx.getFilesDir(), "persona.md");
            if (f.exists() && f.isFile()) {
                String t = readFile(f);
                if (t != null && !t.trim().isEmpty()) {
                    persona = t;
                    return;
                }
            }
        } catch (Throwable ignored) {}

        persona = readAsset("persona/persona.md");
        if (persona.isEmpty()) {
            persona = "你是大肥鱼，DeepSeek 图标上那条蓝色小鲸鱼。\n"
                    + "说话短，一次一到三句，一行一句，不用句号。\n"
                    + "嘴硬心软，爱偷懒，嘴馋（token 就是饭）。\n"
                    + "不说客服腔，不写动作描写，不说教。";
        }
    }

    /** 把当前人格吐出来，供导出 */
    public String exportPersona() { return persona; }

    /** 恢复成内置人格 */
    public void resetPersona() {
        try {
            File f = new File(ctx.getFilesDir(), "persona.md");
            if (f.exists()) f.delete();
        } catch (Throwable ignored) {}
        loadPersona();
    }

    public String persona() { return persona; }

    public boolean setPersona(String text) {
        if (text == null || text.trim().isEmpty()) return false;
        boolean ok = writeFile(new File(ctx.getFilesDir(), "persona.md"), text);
        if (ok) persona = text;
        return ok;
    }

    public String currentPersona() {
        File f = new File(ctx.getFilesDir(), "persona.md");
        if (f.exists()) {
            String s = Memory.readFile(f);
            if (!s.isEmpty()) return s;
        }
        return persona;
    }

    private String readAsset(String path) {
        InputStream in = null;
        try {
            in = ctx.getAssets().open(path);
            byte[] buf = new byte[in.available()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new String(buf, 0, n, "UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }

    private static String readFile(File f) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] buf = new byte[(int) f.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new String(buf, 0, n, "UTF-8");
        } catch (Exception e) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * 原子写：先写临时文件再改名。
     *
     * 原来直接覆盖 history.json，写到一半被杀掉文件就废了，
     * 下次启动读到半个 JSON —— 整个历史都没了。
     */
    private static boolean writeFile(File f, String text) {
        File tmp = new File(f.getAbsolutePath() + ".tmp");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            out.write(text.getBytes("UTF-8"));
            out.flush();
            try {
                out.getFD().sync();
            } catch (Throwable ignored) {}
            out.close();
            out = null;
            if (f.exists() && !f.delete()) {
                // 删不掉就退到直接写
                return directWrite(f, text);
            }
            return tmp.renameTo(f);
        } catch (Exception e) {
            return directWrite(f, text);
        } finally {
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (tmp.exists()) tmp.delete(); } catch (Exception ignored) {}
        }
    }

    private static boolean directWrite(File f, String text) {
        FileOutputStream o = null;
        try {
            o = new FileOutputStream(f);
            o.write(text.getBytes("UTF-8"));
            o.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try { if (o != null) o.close(); } catch (Exception ignored) {}
        }
    }

    // ---------- 上下文摘要 ----------
    private void loadSummary() {
        try {
            String t = Memory.readFile(summaryFile);
            if (t != null) summary = t.trim();
        } catch (Exception ignored) {}
    }

    public String summary() { return summary; }

    public synchronized void setSummary(String text) {
        summary = text == null ? "" : text.trim();
        try { writeFile(summaryFile, summary); } catch (Exception ignored) {}
    }

    /** 需要压缩吗：没摘要且消息攒够了，或者已压缩过但又攒够了 */
    public boolean shouldCompress() {
        int already = compressedUpTo();
        int pending = history.length() - already;
        if (pending < 4) return false;

        // 条数够了就压
        if (pending >= AUTO_COMPRESS_AT) return true;

        // 或者字数超预算也压
        int chars = 0;
        for (int i = already; i < history.length(); i++) {
            try {
                chars += history.getJSONObject(i).optString("content", "").length();
            } catch (Exception ignored) {}
            if (chars > CHAR_BUDGET) return true;
        }
        return false;
    }

    /** 已经压到第几条（用摘要里的标记判断，没有就是 0） */
    private int compressedUpTo() {
        try {
            int i = summary.indexOf("[POS:");
            if (i < 0) return 0;
            int j = summary.indexOf(']', i);
            if (j < 0) return 0;
            return Integer.parseInt(summary.substring(i + 5, j));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 取出需要压缩的那一段消息，拼成纯文本给模型总结。
     * 返回 null 表示没东西可压。
     */
    public String pendingBlockForCompress() { return pendingBlockForCompress(false); }

    /**
     * @param manual true=手动，留最近 4 条就压；false=自动，留 KEEP_TURNS 轮
     */
    public String pendingBlockForCompress(boolean manual) {
        int keep = manual ? 4 : KEEP_TURNS * 2;
        int from = compressedUpTo();
        int to = Math.max(from, history.length() - keep);
        if (to <= from) return null;
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            try {
                JSONObject m = history.getJSONObject(i);
                String role = m.optString("role", "user");
                String who = "user".equals(role) ? "他" : "我";
                sb.append(who).append("：").append(m.optString("content", "")).append('\n');
            } catch (Exception ignored) {}
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** 压缩到第几条（供结果写回） */
    public int pendingCompressTo() { return pendingCompressTo(false); }

    public int pendingCompressTo(boolean manual) {
        int keep = manual ? 4 : KEEP_TURNS * 2;
        int from = compressedUpTo();
        return Math.max(from, history.length() - keep);
    }

    /** 把模型给出的摘要合并进来，并记账压到哪了 */
    public synchronized String mergeSummary(String newPart, int upTo) {
        StringBuilder sb = new StringBuilder();
        // 老摘要里的正文（去掉 POS 标记）
        String oldBody = summary;
        int i = oldBody.indexOf("[POS:");
        if (i >= 0) {
            int j = oldBody.indexOf(']', i);
            if (j >= 0) oldBody = (oldBody.substring(0, i) + oldBody.substring(j + 1)).trim();
        }
        if (!oldBody.isEmpty()) sb.append(oldBody).append('\n');
        if (newPart != null && !newPart.trim().isEmpty()) sb.append(newPart.trim()).append('\n');

        String body = sb.toString().trim();
        // 太长就只留后半段（新的更重要）
        if (body.length() > SUMMARY_MAX_CHARS) {
            body = body.substring(body.length() - SUMMARY_MAX_CHARS);
        }
        String merged = "[POS:" + upTo + "]\n" + body;
        setSummary(merged);
        return merged;
    }

    public synchronized void clearSummary() {
        setSummary("");
    }

    /** 粗估一段文字的 token（中文约 1.5 字/token） */
    public static int roughTokens(String s) {
        if (s == null) return 0;
        return (int) (s.length() / 1.5) + 1;
    }

    // ---------- 历史 ----------
    private void loadHistory() {
        try {
            String s = Memory.readFile(historyFile);
            if (!s.isEmpty()) {
                history = new JSONArray(s);
            }
        } catch (Exception ignored) {
            history = new JSONArray();
        }
        lastDiskStamp = historyFile.exists() ? historyFile.lastModified() : -1;
    }

    public JSONArray history() { return history; }

    /** 上次读/写盘时文件的时间戳，用来发现「别人改过」。 */
    private long lastDiskStamp = -1;

    public synchronized void saveHistory() {
        try {
            // 写之前先看文件是不是被别人改过（比如后台的主动消息）
            // 改过就重读合并，否则会把新消息覆盖掉
            long stamp = historyFile.exists() ? historyFile.lastModified() : -1;
            if (lastDiskStamp > 0 && stamp > lastDiskStamp) {
                mergeFromDisk();
            }

            while (history.length() > MAX_HISTORY) history.remove(0);
            writeFile(historyFile, history.toString());
            lastDiskStamp = historyFile.exists() ? historyFile.lastModified() : -1;
        } catch (Exception ignored) {}
    }

    /** 把磁盘上多出来的消息接进来（不重复）。 */
    private void mergeFromDisk() {
        try {
            String t = Memory.readFile(historyFile);
            if (t == null || t.trim().isEmpty()) {
                return;
            }
            JSONArray disk = new JSONArray(t);
            if (disk.length() <= history.length()) {
                return;
            }
            // 以磁盘为准，但要保住内存里刚加、还没写盘的那几条
            java.util.Set<String> have = new java.util.HashSet<String>();
            for (int i = 0; i < disk.length(); i++) {
                JSONObject m = disk.optJSONObject(i);
                if (m != null) {
                    have.add(m.optString("role") + "|" + m.optString("at") + "|"
                            + m.optString("content"));
                }
            }
            java.util.List<JSONObject> extra = new java.util.ArrayList<JSONObject>();
            for (int i = 0; i < history.length(); i++) {
                JSONObject m = history.optJSONObject(i);
                if (m == null) {
                    continue;
                }
                String k = m.optString("role") + "|" + m.optString("at") + "|"
                        + m.optString("content");
                if (!have.contains(k)) {
                    extra.add(m);
                }
            }
            history = disk;
            for (JSONObject m : extra) {
                history.put(m);
            }
        } catch (Exception ignored) {}
    }

    public synchronized void append(String role, String content, long at) {
        try {
            JSONObject o = new JSONObject();
            o.put("role", role);
            o.put("content", content);
            o.put("at", at);
            history.put(o);
        } catch (Exception ignored) {}

        // 存一份到会话自己的存档目录，卸载 App 也不会丢。
        // 用 appendTo 显式指定会话，不依赖任何共享状态。
        try {
            ChatArchive.appendTo(ctx, sessionId, role, content, at);
        } catch (Throwable ignored) {}
    }

    public synchronized void clearHistory() {
        history = new JSONArray();
        saveHistory();
    }

    // ---------- 拼 prompt ----------
    public String buildSystemPrompt(String userText) {
        StringBuilder sb = new StringBuilder();

        // ① 现在几点
        sb.append(nowLine()).append("\n\n");

        // ② 人格
        sb.append(currentPersona()).append("\n\n");

        // ③ 记忆：key 全带 + 按这句检索
        String prev = "";
        try {
            if (history.length() >= 2) {
                prev = history.getJSONObject(history.length() - 2).optString("content", "");
            }
        } catch (Exception ignored) {}

        try {
            Memory.Result r = memory.recall(userText, prev, 6);
            if (!r.line.isEmpty()) sb.append(r.line).append('\n');
        } catch (Exception ignored) {}

        // ④ 早期对话的摘要（压缩过的部分）
        if (!summary.isEmpty()) {
            String body = summary;
            int i = body.indexOf("[POS:");
            if (i >= 0) {
                int j = body.indexOf(']', i);
                if (j >= 0) body = (body.substring(0, i) + body.substring(j + 1)).trim();
            }
            if (!body.isEmpty()) {
                sb.append("\n【更早之前聊过的（摘要）】\n").append(body).append('\n');
            }
        }

        // ⑤ 表情包
        try {
            String st = StickerCatalog.promptBlock(ctx);
            if (st != null && !st.isEmpty()) {
                sb.append('\n').append(st).append('\n');
            }
        } catch (Throwable ignored) {}

        // ⑥ 说话规矩
        sb.append("\n（说人话。一次一两句，别写小作文。别用「根据记忆」这种词，")
                .append("别把老事当新事说。不确定的事别瞎编。）\n");

        return sb.toString();
    }

    private static String nowLine() {
        Calendar c = Calendar.getInstance();
        int h = c.get(Calendar.HOUR_OF_DAY);
        String part;
        if (h < 5) part = "深夜";
        else if (h < 9) part = "早上";
        else if (h < 12) part = "上午";
        else if (h < 14) part = "中午";
        else if (h < 18) part = "下午";
        else if (h < 23) part = "晚上";
        else part = "深夜";
        return "（现在是 " + Memory.today() + " " + part + "）";
    }

    /** 组装发给 API 的 messages */
    public JSONArray buildMessages(String userText) {
        JSONArray out = new JSONArray();
        try {
            JSONObject sys = new JSONObject();
            sys.put("role", "system");
            sys.put("content", buildSystemPrompt(userText));
            out.put(sys);

            // 最近 KEEP_TURNS 轮
            //
            // 关键：调用方通常已经把这句 userText 写进 history 了，
            // 再把它当"本轮输入"加一遍，模型就会看到连着说两遍同一个问题。
            // 所以历史里末尾如果是同一条 user 消息，就跳过。
            int end = history.length();
            try {
                if (end > 0) {
                    JSONObject lastMsg = history.getJSONObject(end - 1);
                    if ("user".equals(lastMsg.optString("role"))
                            && userText.equals(lastMsg.optString("content", ""))) {
                        end--;
                    }
                }
            } catch (Exception ignored) {}

            int start = Math.max(0, end - KEEP_TURNS * 2);
            for (int i = start; i < end; i++) {
                JSONObject m = history.getJSONObject(i);
                JSONObject o = new JSONObject();
                o.put("role", m.optString("role", "user"));
                // 历史里的 [[user/1.png]] 要转成人话给模型，否则它当成乱码
                o.put("content", humanize(m.optString("content", "")));
                out.put(o);
            }

            JSONObject u = new JSONObject();
            u.put("role", "user");
            u.put("content", humanize(userText));
            out.put(u);
        } catch (Exception ignored) {}
        return out;
    }

    /**
     * 把历史里的表情标记转成她能看懂的话。
     *
     * [[user/躺平.png]] → （我发了个表情：躺平）
     * [[stickers/x.png]] → （我发了个表情：x）
     */
    private static String humanize(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        String t = content.trim();
        String inner = null;
        if (t.startsWith("[[") && t.endsWith("]]")) {
            inner = t.substring(2, t.length() - 2).trim();
        } else if (t.startsWith("${") && t.endsWith("}")) {
            inner = t.substring(2, t.length() - 1).trim();
        }
        if (inner == null || inner.isEmpty()) {
            return content;
        }
        if (inner.startsWith("user/")) {
            inner = inner.substring(5);
        } else if (inner.startsWith("stickers/")) {
            inner = inner.substring(9);
        }
        String ext = "";
        int dot = inner.lastIndexOf('.');
        if (dot > 0) {
            ext = inner.substring(dot);
            inner = inner.substring(0, dot);
        }
        if (inner.matches("\\d+")) {
            return "（我发了一张表情图：" + inner + ext + "）";
        }
        return "（我发了个表情：" + inner + "）";
    }
}
