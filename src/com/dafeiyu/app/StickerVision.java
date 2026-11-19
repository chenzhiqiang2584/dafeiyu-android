package com.dafeiyu.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 用 DeepSeek 视觉模型给表情包生成描述。
 *
 * 只在「添加表情」时调用一次，描述存到 sticker_names.json。
 * 之后她读文字就知道每张图是什么，不用每次发图都花 token。
 *
 * 模型 deepseek-flash，图片走 base64 内联，detail=low 省 token。
 */
public final class StickerVision {

    private static final String API = "https://api.deepseek.com/chat/completions";
    private static final String MODEL = "deepseek-flash";

    /** 生成的描述太长了没用，限制在 20 字内。 */
    private static final int MAX_DESC = 20;

    /** 上次失败的原因，给界面显示用。 */
    public static volatile String lastError = null;

    private StickerVision() {
    }

    /**
     * 给一张表情生成描述。阻塞调用，必须放后台线程。
     *
     * @return 描述（如「躺着睡觉，很困」），失败返回 null
     */
    public static String describe(Context ctx, File img) {
        if (img == null || !img.isFile()) {
            return null;
        }
        String key = StickerCatalog.apiKey(ctx);
        if (key.isEmpty()) {
            lastError = "没填 API Key";
            return null;
        }
        HttpURLConnection conn = null;
        try {
            // 缩到 256 再传，省流量也省 token
            String b64 = toBase64(img, 256);
            if (b64 == null) {
                return null;
            }

            JSONObject body = new JSONObject();
            body.put("model", MODEL);
            body.put("stream", false);
            body.put("max_tokens", 80);

            JSONArray msgs = new JSONArray();
            JSONObject m = new JSONObject();
            m.put("role", "user");

            JSONArray content = new JSONArray();

            JSONObject t = new JSONObject();
            t.put("type", "text");
            t.put("text", "这是一张聊天用的表情包。用一句简短的中文描述它的表情和情绪，"
                    + "不超过 15 个字，例如「躺着睡觉，很困」「指着人，无语」。"
                    + "只输出描述本身，不要引号，不要解释。");
            content.put(t);

            JSONObject imgBlock = new JSONObject();
            imgBlock.put("type", "image_url");
            JSONObject iu = new JSONObject();
            iu.put("url", "data:image/png;base64," + b64);
            iu.put("detail", "low");
            imgBlock.put("image_url", iu);
            content.put(imgBlock);

            m.put("content", content);
            msgs.put(m);
            body.put("messages", msgs);

            conn = (HttpURLConnection) new URL(API).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(40000);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + key);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            if (code != 200) {
                lastError = "HTTP " + code;
                return null;
            }

            BufferedReader r = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            r.close();

            JSONObject resp = new JSONObject(sb.toString());
            JSONArray choices = resp.optJSONArray("choices");
            if (choices == null || choices.length() == 0) {
                return null;
            }
            JSONObject c0 = choices.optJSONObject(0);
            if (c0 == null) {
                return null;
            }
            JSONObject msg = c0.optJSONObject("message");
            if (msg == null) {
                return null;
            }
            String text = msg.optString("content", "").trim();
            text = cleanup(text);
            return text.isEmpty() ? null : text;
        } catch (Throwable t) {
            lastError = t.getClass().getSimpleName() + ": " + t.getMessage();
            return null;
        } finally {
            if (conn != null) {
                try {
                    conn.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /** 去掉引号、换行、多余标点。 */
    private static String cleanup(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        // 去掉包裹的引号
        t = t.replaceAll("^[\"'「『（(]+", "").replaceAll("[\"'」』）)]+$", "");
        // 只留第一行
        int nl = t.indexOf('\n');
        if (nl > 0) {
            t = t.substring(0, nl).trim();
        }
        // 去掉「这张图片」这类开头
        t = t.replaceAll("^(这张|该)?(图片|表情包?|图)(里|中)?(是|有|显示)?", "").trim();
        t = t.replaceAll("^[:：,，]", "").trim();
        if (t.length() > MAX_DESC) {
            t = t.substring(0, MAX_DESC);
        }
        return t;
    }

    /** 读文件、缩到 max 边、转 base64。 */
    private static String toBase64(File f, int max) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            int w = o.outWidth, h = o.outHeight;
            if (w <= 0 || h <= 0) {
                // 可能是 GIF，用 Movie 取第一帧
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                android.graphics.Movie mv = android.graphics.Movie.decodeStream(in);
                in.close();
                if (mv == null) {
                    return null;
                }
                Bitmap b = Bitmap.createBitmap(Math.max(1, mv.width()),
                        Math.max(1, mv.height()), Bitmap.Config.ARGB_8888);
                mv.setTime(0);
                mv.draw(new android.graphics.Canvas(b), 0, 0);
                return bmpToB64(b, max);
            }
            int sample = 1;
            while (Math.max(w / sample, h / sample) > max * 2) {
                sample *= 2;
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
            if (b == null) {
                return null;
            }
            return bmpToB64(b, max);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String bmpToB64(Bitmap src, int max) {
        Bitmap b = src;
        int w = src.getWidth(), h = src.getHeight();
        if (Math.max(w, h) > max) {
            float s = (float) max / Math.max(w, h);
            b = Bitmap.createScaledBitmap(src, Math.max(1, (int) (w * s)),
                    Math.max(1, (int) (h * s)), true);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 85, bos);
        byte[] bytes = bos.toByteArray();
        try {
            bos.close();
        } catch (Throwable ignored) {
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }
}
