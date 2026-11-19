package com.dafeiyu.app;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天搜索。
 *
 * 同时搜两个地方：当前窗口里的对话 + 存档里的全部历史。
 */
public final class ChatSearch {

    private ChatSearch() {
    }

    public static void open(final Activity act, final Chat chat) {
        final EditText et = new EditText(act);
        et.setHint("搜点什么…比如「生日」");
        et.setTextSize(15.5f);
        FontKit.r(act, et);

        final TextView result = new TextView(act);
        result.setTextSize(12.5f);
        result.setTextColor(Theme.text());
        result.setLineSpacing(0f, 1.3f);
        FontKit.r(act, result);

        ScrollView sv = new ScrollView(act);
        sv.addView(result);

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * act.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        box.addView(et);
        box.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView searchBtn = MDialog.pill(act, "搜", true);
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String q = et.getText().toString().trim();
                if (q.isEmpty()) {
                    result.setText("输入关键字再搜");
                    return;
                }
                result.setText("搜索中…");
                doSearch(act, chat, q, result);
            }
        });
        box.addView(searchBtn);

        MDialog.cardView(act, "搜索聊天记录", box, "关闭").show();
    }

    private static void doSearch(final Activity act, final Chat chat, final String q,
                                 final TextView out) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                StringBuilder sb = new StringBuilder();

                // 1. 当前窗口
                List<String> live = searchLive(chat, q);
                sb.append("=== 当前对话（").append(live.size()).append(" 条）===\n");
                if (live.isEmpty()) {
                    sb.append("（没有）\n");
                } else {
                    for (int i = 0; i < Math.min(live.size(), 120); i++) {
                        sb.append(live.get(i)).append('\n');
                    }
                }

                // 2. 存档
                List<String> arch = ChatArchive.search(q);
                sb.append("\n=== 历史存档（").append(arch.size()).append(" 条）===\n");
                if (arch.isEmpty()) {
                    sb.append("（没有）\n");
                } else {
                    for (int i = 0; i < Math.min(arch.size(), 200); i++) {
                        sb.append(arch.get(i)).append('\n');
                    }
                }

                final String text = sb.toString();
                act.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        out.setText(text);
                    }
                });
            }
        }, "search").start();
    }

    private static List<String> searchLive(Chat chat, String q) {
        List<String> out = new ArrayList<String>();
        try {
            String key = q.toLowerCase(java.util.Locale.US);
            JSONArray h = chat.history();
            for (int i = 0; i < h.length(); i++) {
                JSONObject m = h.optJSONObject(i);
                if (m == null) {
                    continue;
                }
                String c = m.optString("content", "");
                if (c.toLowerCase(java.util.Locale.US).contains(key)) {
                    String who = "user".equals(m.optString("role")) ? "我" : "她";
                    out.add(who + "：" + oneLine(c));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static String oneLine(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\n", " ").trim();
        return t.length() > 60 ? t.substring(0, 60) + "…" : t;
    }
}
