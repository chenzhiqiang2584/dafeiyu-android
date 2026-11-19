package com.dafeiyu.app;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.app.Dialog;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 查 DeepSeek 余额。
 *
 * 原来的实现只在「测一下 Key」时顺带返回一个 total_balance，而且写死了 ¥。
 * DeepSeek 返回的 balance_infos 是数组，可能有 CNY 和 USD 两组，
 * 取第一组再配死符号，币种错了数字就是错的。
 */
public final class BalanceChecker {

    private static final String API = "https://api.deepseek.com/user/balance";
    private static final Handler UI = new Handler(Looper.getMainLooper());

    private BalanceChecker() {
    }

    public static String symbolOf(String currency) {
        if (currency == null) {
            return "";
        }
        String u = currency.trim().toUpperCase();
        if ("CNY".equals(u) || "RMB".equals(u)) {
            return "¥";
        }
        if ("USD".equals(u)) {
            return "$";
        }
        if ("EUR".equals(u)) {
            return "€";
        }
        return u + " ";
    }

    public static void show(final Activity act, final String key) {
        if (key == null || key.trim().isEmpty()) {
            SimpleDialog.alert(act, "余额", "还没填 API Key。\n先去设置里填一个。");
            return;
        }
        final TextView tv = new TextView(act);
        int pad = (int) (18 * act.getResources().getDisplayMetrics().density);
        tv.setTextSize(14.5f);
        tv.setTextColor(Theme.text());
        tv.setPadding(pad, pad, pad, pad);
        tv.setText("正在查…");
        FontKit.r(act, tv);

        ScrollView sv = new ScrollView(act);
        sv.addView(tv);

        final Dialog dlg = MDialog.cardView(act, "DeepSeek 余额", sv, "关闭");
        dlg.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            @Override
            public void onShow(android.content.DialogInterface d) {
            }
        });
        dlg.show();
        query(key, tv);
    }

    private static void query(final String key, final TextView out) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String text = fetch(key);
                UI.post(new Runnable() {
                    @Override
                    public void run() {
                        if (out != null) {
                            out.setText(text);
                        }
                    }
                });
            }
        }, "balance").start();
    }

    public static String fetch(String key) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(API).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("Authorization", "Bearer " + key.trim());
            int code = conn.getResponseCode();
            if (code == 401) {
                return "Key 不对（401）\n\n去设置里重新填一遍。";
            }
            if (code == 402) {
                return "余额不足（402）\n\n去 platform.deepseek.com 充值。";
            }
            if (code != 200) {
                return "接口返回 " + code + "\n\n" + readAll(conn.getErrorStream());
            }
            return format(readAll(conn.getInputStream()));
        } catch (javax.net.ssl.SSLException e) {
            return "网络证书问题：连不上 api.deepseek.com";
        } catch (java.net.UnknownHostException e) {
            return "连不上网（找不到 api.deepseek.com）";
        } catch (Throwable t) {
            return "查余额失败：" + t.getClass().getSimpleName() + "\n" + t.getMessage();
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public static String format(String body) {
        StringBuilder sb = new StringBuilder();
        try {
            JSONObject j = new JSONObject(body);
            boolean available = j.optBoolean("is_available", true);
            JSONArray arr = j.optJSONArray("balance_infos");
            if (arr == null || arr.length() == 0) {
                return "查到的是：\n" + body;
            }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String cur = o.optString("currency", "");
                String total = o.optString("total_balance", "?");
                String granted = o.optString("granted_balance", "");
                String topped = o.optString("topped_up_balance", "");
                String sym = symbolOf(cur);

                sb.append(i == 0 ? "可用余额：" : "另有：").append(sym).append(total);
                if (!cur.isEmpty() && sym.endsWith(" ")) {
                    sb.append(' ').append(cur);
                }
                sb.append('\n');
                if (!topped.isEmpty()) {
                    sb.append("  充值：").append(sym).append(topped).append('\n');
                }
                if (!granted.isEmpty()) {
                    sb.append("  赠送：").append(sym).append(granted).append('\n');
                }
                sb.append('\n');
            }
            if (!available) {
                sb.append("状态：不可用（余额可能已经用完）\n\n");
            }
            sb.append("—— platform.deepseek.com 可充值 / 看账单");
        } catch (Throwable t) {
            return "解析失败：" + t.getClass().getSimpleName() + "\n\n" + body;
        }
        return sb.toString();
    }

    private static String readAll(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            in.close();
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }
}
