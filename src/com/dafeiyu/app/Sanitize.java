package com.dafeiyu.app;

import java.util.regex.Pattern;

/**
 * 把模型回复里的「后台杂音」剥掉，只留给她说的话。
 *
 * 这些人格文件里要求她每轮都写 <状态>{...}</状态>，那是给情绪系统用的。
 * 网桥那边会挖出来存掉，剩下的才显示。独立版没那么一套，至少不能让他看见。
 *
 * 规则完全照搬 xinjia/bridge.mjs 里那段加固（2026-11-13 反复翻车攒出来的）。
 */
public final class Sanitize {

    private Sanitize() {}

    // 完整剥除的块
    private static final Pattern P_STATE_BLOCK =
            Pattern.compile("<状态>[\\s\\S]*?</状态>");
    private static final Pattern P_STATE_B =
            Pattern.compile("<b>?\\s*\\[状态\\][\\s\\S]*?</b>", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_STATE_BARE =
            Pattern.compile("\\[状态\\]\\s*\\{[\\s\\S]*?\\}?");
    private static final Pattern P_REPLY_TAG =
            Pattern.compile("</?reply[^>]*>");
    private static final Pattern P_B_TAG =
            Pattern.compile("</?b>", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_LINE_TAG =
            Pattern.compile("^\\s*\\[[^\\]]{1,6}\\]", Pattern.MULTILINE);
    private static final Pattern P_JSON_TIER =
            Pattern.compile("\\{\\s*\"tier\"[\\s\\S]*\\}");
    private static final Pattern P_CODE_FENCE =
            Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern P_JSON_OBJ =
            Pattern.compile("\\{[\\s\\S]{0,800}?\"[\\w]+\"\\s*:\\s*[\\s\\S]{0,800}?\\}");
    private static final Pattern P_LONE_BRACE =
            Pattern.compile("^\\s*[\\[{]\\s*$", Pattern.MULTILINE);
    private static final Pattern P_LONE_CLOSE =
            Pattern.compile("^\\s*[}\\]]\\s*,?\\s*$", Pattern.MULTILINE);
    private static final Pattern P_MCP_LOG =
            Pattern.compile("^\\s*\\[mcp\\][^\\n]*", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern P_DOUYIN_LOG =
            Pattern.compile("^\\s*\\[douyin[^\\]]*\\][^\\n]*", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern P_KV_LINE =
            Pattern.compile("^\\s*\"[\\w_]+\"\\s*:\\s*.*$", Pattern.MULTILINE);
    private static final Pattern P_BLANK3 =
            Pattern.compile("\\n{3,}");
    private static final Pattern P_JUNK =
            Pattern.compile("^[\\s\"'\\{\\}\\[\\]\\(\\),:;]+$");

    /**
     * 剥掉所有不该给他看的东西。
     * @param raw 模型原始输出
     * @return 应当显示的文字（可能为空串，调用方负责兜底）
     */
    public static String clean(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        String s = raw;
        s = P_STATE_BLOCK.matcher(s).replaceAll("");
        s = P_STATE_B.matcher(s).replaceAll("");
        s = P_STATE_BARE.matcher(s).replaceAll("");
        s = P_REPLY_TAG.matcher(s).replaceAll("");
        s = P_B_TAG.matcher(s).replaceAll("");
        s = P_LINE_TAG.matcher(s).replaceAll("");
        s = P_JSON_TIER.matcher(s).replaceAll("");
        s = P_CODE_FENCE.matcher(s).replaceAll("");
        s = P_JSON_OBJ.matcher(s).replaceAll("");
        s = P_LONE_BRACE.matcher(s).replaceAll("");
        s = P_LONE_CLOSE.matcher(s).replaceAll("");
        s = P_MCP_LOG.matcher(s).replaceAll("");
        s = P_DOUYIN_LOG.matcher(s).replaceAll("");
        s = P_KV_LINE.matcher(s).replaceAll("");
        s = P_BLANK3.matcher(s).replaceAll("\n\n");
        s = s.trim();

        if (P_JUNK.matcher(s).matches()) s = "";
        return s;
    }

    /**
     * 流式显示用：把还没收完的半截状态块也挡住，
     * 否则打字机效果会先闪出一串 JSON。
     */
    public static String cleanStreaming(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        int open = raw.indexOf("<状态>");
        if (open >= 0) {
            int close = raw.indexOf("</状态>", open);
            if (close < 0) {
                // 还没收完，先只显示状态块前面那部分
                return clean(raw.substring(0, open));
            }
            // 收完了：把这一块挖掉，后面继续
            String cut = raw.substring(0, open) + raw.substring(close + "</状态>".length());
            return clean(cut);
        }
        // 可能是半截的 "<状"
        if (raw.endsWith("<") || raw.endsWith("<状") || raw.endsWith("<状态")) {
            int lt = raw.lastIndexOf('<');
            return clean(raw.substring(0, lt));
        }
        return clean(raw);
    }

    /** 一句大概多长就断开（去掉标点后）。 */
    private static final int SOFT_MAX = 60;
    /** 硬上限：超过这个长度不管标点都要断。 */
    private static final int HARD_MAX = 68;

    /**
     * 把过长的行断开。
     *
     * 模型有时会把一整段话写成一行（60 多字），
     * 界面上就是一个超宽气泡，折行后很难看。
     * 这里按标点切开，每段一个气泡，读起来才像在聊天。
     *
     * 不会切 ${xxx} 表情标记，也不会切句子中间的词。
     */
    public static String splitLines(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        String[] rawLines = text.split("\n");
        StringBuilder out = new StringBuilder();

        for (String raw : rawLines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            // 短行直接留
            if (line.length() <= SOFT_MAX) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(line);
                continue;
            }

            // 表情标记整行不动
            if (line.startsWith("${") && line.endsWith("}")) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(line);
                continue;
            }

            // 按标点切
            java.util.List<String> parts = new java.util.ArrayList<String>();
            StringBuilder cur = new StringBuilder();
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                cur.append(c);

                boolean atPunct = (c == '，' || c == '。' || c == '！' || c == '？'
                        || c == '、' || c == '；' || c == '…'
                        || c == ',' || c == '.' || c == '!' || c == '?'
                        || c == ';');

                // 逗号类：够长了就切
                boolean cutHere = false;
                if (atPunct) {
                    String body = stripEnd(cur.toString());
                    if (c == '，' || c == ',' || c == '、') {
                        cutHere = body.length() >= 24;      // 逗号处 24 字以上才切
                    } else {
                        cutHere = body.length() >= 12;
                    }
                }
                // 太长还没遇到标点，硬切
                if (!cutHere && stripEnd(cur.toString()).length() >= HARD_MAX) {
                    cutHere = true;
                }
                if (cutHere) {
                    String piece = cur.toString().trim();
                    if (!piece.isEmpty()) {
                        parts.add(piece);
                    }
                    cur.setLength(0);
                }
            }
            String tail = cur.toString().trim();
            if (!tail.isEmpty()) {
                parts.add(tail);
            }

            for (String piece : parts) {
                if (out.length() > 0) {
                    out.append('\n');
                }
                out.append(piece);
            }
        }
        return out.toString();
    }

    private static String stripEnd(String s) {
        int n = s.length();
        while (n > 0 && "，。！？、；…,.!?; ".indexOf(s.charAt(n - 1)) >= 0) {
            n--;
        }
        return s.substring(0, n);
    }
}
