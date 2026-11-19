package com.dafeiyu.app;

import android.content.Context;
import android.content.res.AssetManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 表情包目录。
 *
 * assets/stickers/ 下面是 284 个用哈希命名的图（gif/png/jpg），
 * 文件名对模型没意义，所以这里做两件事：
 *   1. 列出全部文件名，给面板用
 *   2. 生成一段「可以用表情」的 prompt 片段，告诉模型格式 `${文件名}`
 *
 * 模型输出 ${文件名} 就发那张图。
 */
public final class StickerCatalog {

    private static final String DIR = "stickers";
    private static List<String> names;
    private static String promptBlock;

    /** 表情内容表：文件名 -> 描述。让模型知道每张图是什么，才挑得准。 */
    private static final String[][] DESC = {
        {"早呀", "表情", "挥手打招呼，早上好，笑眯眯"},
        {"好耶", "表情", "跳起来欢呼，超开心"},
        {"贴贴", "表情", "抱着大爱心蹭，撒娇"},
        {"想你啦", "表情", "双手捧心，眼巴巴看着，想你"},
        {"喜欢你", "表情", "双手比心，脸红，喜欢你"},
        {"小心心", "表情", "递出一颗小爱心"},
        {"谢谢", "表情", "低头微笑道谢"},
        {"投喂", "表情", "端着饭碗眼巴巴，想吃饭"},
        {"拜托拜托", "表情", "双手合十恳求，拜托"},
        {"乖巧", "表情", "端正坐着，很乖"},
        {"等你", "表情", "双手捧脸，安静等待"},
        {"才没有", "表情", "红着脸摆手否认"},
        {"震惊", "表情", "瞪大眼睛张嘴，非常震惊"},
        {"迷茫", "表情", "歪头满脸问号，困惑"},
        {"困困", "表情", "抱着枕头打哈欠，很困"},
        {"躺平", "表情", "四仰八叉躺着，摆烂"},
        {"收到", "表情", "敬礼应答，收到"},
        {"包身上", "表情", "拍胸脯打包票，包在我身上"},
        {"你最棒", "表情", "双手竖大拇指夸你"},
        {"晚安", "表情", "盖被子睡，晚安"},
        {"开心", "表情", "趴着笑眯眯，很开心（开心~）"},
        {"抱抱2", "表情", "抱着 deepseek 鲸鱼玩偶求抱（抱抱~）"},
        {"啊", "表情", "震惊捂脸瞪眼（啊?!）"},
        {"太棒了", "表情", "双手握拳超兴奋（太棒了！）"},
        {"努力中", "表情", "对着笔记本认真敲字（正在努力中…）"},
        {"呜呜2", "表情", "哭着擦眼泪（呜呜…）"},
        {"害羞", "表情", "捂脸脸红（害羞~）"},
        {"生气了", "表情", "鼓脸竖眉，生气了（生气了！）"},
        {"再见", "表情", "挥手道别（再见~）"},
        {"帅", "表情", "戴墨镜很酷（帅！）"},
        {"有点难", "表情", "垂头丧气，觉得难（呜…有点难…）"},
        {"有你真好", "表情", "举着「有你真好」的牌子"},
        {"好感动", "表情", "捂着嘴感动（好感动…）"},
        {"休息一下", "表情", "靠着杯子歇一会（休息一下~）"},
        {"一直陪着", "表情", "抱着鲸鱼玩偶，一直陪着你"},
        {"寿来", "表情", "张开双手扑过来，超兴奋（寿来！）"},
        {"耶2", "表情", "比剪刀手欢呼（耶~）"},
        {"哼3", "表情", "抱着手臂别过头，傲娇不屑（哼！）"},
        {"好吃3", "表情", "端着饭碗吃白饭，很满足（好吃~~）"},
        {"问号3", "表情", "歪头满脸问号，疑惑"},
        {"睡着2", "表情", "趴着睡着了，Zzz"},
        {"没问题", "表情", "抱着手臂自信点头（没问题！）"},
        {"来吧", "表情", "张开双臂兴奋大喊（来吧！！）"},
        {"最喜欢", "表情", "抱着鲸鱼玩偶，最喜欢小鲸鱼了"},
    };

    private StickerCatalog() {
    }

    /** 全部表情文件名（不含路径）。 */
    public static synchronized List<String> all(Context c) {
        if (names != null) {
            return names;
        }
        List<String> out = new ArrayList<String>();
        try {
            AssetManager am = c.getAssets();
            String[] fs = am.list(DIR);
            if (fs != null) {
                for (String f : fs) {
                    if (f.endsWith(".gif") || f.endsWith(".png")
                            || f.endsWith(".jpg") || f.endsWith(".jpeg")) {
                        out.add(f);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        names = out;
        return names;
    }

    /** 用户自己加的表情（Documents/大肥鱼表情/）。 */
    public static synchronized List<String> userAll(Context c) {
        List<String> out = new ArrayList<String>();
        try {
            // 直接用公共文档目录，不走 StickerPanel.userDir（那个需要 Activity）
            java.io.File d = null;
            java.io.File docs = android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS);
            if (docs != null) {
                java.io.File cand = new java.io.File(docs, "大肥鱼表情");
                if (cand.exists()) {
                    d = cand;
                }
            }
            // 退回私有目录
            if (d == null) {
                d = new java.io.File(c.getFilesDir(), "stickers");
            }
            java.io.File[] fs = d.listFiles();
            if (fs != null) {
                for (java.io.File f : fs) {
                    if (!f.isFile()) {
                        continue;
                    }
                    String n = f.getName().toLowerCase(java.util.Locale.US);
                    if (n.endsWith(".png") || n.endsWith(".jpg")
                            || n.endsWith(".jpeg") || n.endsWith(".gif")) {
                        out.add(f.getName());
                    }
                }
            }
            java.util.Collections.sort(out);
        } catch (Throwable ignored) {
        }
        return out;
    }

    /** 读 API Key（给视觉识别用）。 */
    public static String apiKey(Context c) {
        try {
            // 必须和 MainActivity.PREF 一致。之前写成 "dafeiyu"，读的是空文件，
            // 所以表情识别和每日记忆都报「没填 API Key」。
            return c.getSharedPreferences(MainActivity.PREF, Context.MODE_PRIVATE)
                    .getString(MainActivity.K_APIKEY, "").trim();
        } catch (Throwable t) {
            return "";
        }
    }

    public static String path(String name) {
        return DIR + "/" + name;
    }

    /** 这是不是一张表情图。 */
    public static boolean isSticker(Context c, String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        return all(c).contains(name);
    }

    /**
     * 给 system prompt 用的说明。
     *
     * 关键：不能把 284 个文件名全塞进去，太费 token。
     * 只说格式，模型想发表情时随便挑一个文件名即可 —— 挑错就当她没发。
     * 但这样模型可能瞎编文件名，所以再给一小撮真实样例。
     */
    public static synchronized String promptBlock(Context c) {
        if (promptBlock != null) {
            return promptBlock;
        }
        List<String> all = all(c);
        List<String> users = userAll(c);
        if (all.isEmpty() && users.isEmpty()) {
            promptBlock = "";
            return promptBlock;
        }

        java.util.HashMap<String, String> descMap = new java.util.HashMap<String, String>();
        for (String[] row : DESC) {
            if (row.length >= 3) {
                descMap.put(row[0], row[2]);
            }
        }

        // 所有可用的文件名（不含扩展名）
        java.util.List<String> stems = new java.util.ArrayList<String>();
        java.util.Map<String, String> stemToFile = new java.util.HashMap<String, String>();
        for (String f : all) {
            String stem = f;
            int dot = stem.lastIndexOf('.');
            if (dot > 0) {
                stem = stem.substring(0, dot);
            }
            stems.add(stem);
            stemToFile.put(stem, f);
        }
        java.util.Map<String, String> ud = loadUserDesc(c);
        for (String f : users) {
            String stem = stripExt(f);
            if (!stemToFile.containsKey(stem)) {
                stems.add(stem);
                stemToFile.put(stem, f);
            }
            // 用户表情如果识别过，把描述也加进 descMap
            String d = ud.get(stem);
            if (d != null && !d.trim().isEmpty()) {
                descMap.put(stem, d);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# 表情\n");
        sb.append("你可以发表情。格式是 ${名字}，单独占一行。\n");
        sb.append("表情本身就是一条完整的话，不要为了发表情再配一句文字说明。\n");
        sb.append("想发就只发 ${名字} 这一行，什么都不用说。\n");
        sb.append("也可以文字和表情同时发，但那是因为你本来就有话说，不是为了解释表情。\n");
        sb.append("一次最多一张。\n\n");
        sb.append("看到他说「我发了个表情：xxx」时，那是他发给你的图，")
                .append("顺着这个回应就行，不要说「我看不懂」「看不到图」。\n");
        sb.append("他自己发的那张，你一般不用再发回去。")
                .append("通常回文字就够了，或者换一张意思不同的。\n\n");
        sb.append("只能用下面这些名字，不许自己编：\n");
        for (String stem : stems) {
            String d = descMap.get(stem);
            sb.append("  ").append(stem);
            if (d != null) {
                sb.append(" — ").append(d);
            }
            sb.append('\n');
        }
        promptBlock = sb.toString();
        return promptBlock;
    }

    /** 从一段文本里抠出表情文件名（如果她写了）。 */
    public static String extract(Context c, String text) {
        if (text == null) {
            return null;
        }
        int a = text.indexOf("${");
        while (a >= 0) {
            int b = text.indexOf('}', a);
            if (b < 0) {
                return null;
            }
            String inner = text.substring(a + 2, b).trim();
            if (isSticker(c, inner)) {
                return inner;
            }
            a = text.indexOf("${", b);
        }
        // 兼容 [[名字]] 写法
        a = text.indexOf("[[");
        while (a >= 0) {
            int b = text.indexOf("]]", a);
            if (b < 0) {
                return null;
            }
            String inner = text.substring(a + 2, b).trim();
            if (isSticker(c, inner)) {
                return inner;
            }
            a = text.indexOf("[[", b);
        }
        return null;
    }

    /** 把 ${xxx} / [[xxx]] 从文本里去掉，只留文字。 */
    public static String strip(String text) {
        if (text == null) {
            return "";
        }
        String s = text.replaceAll("\\$\\{[^}]{0,120}\\}", "");
        s = s.replaceAll("\\[\\[[^\\]]{0,120}\\]\\]", "");
        return s.trim();
    }

    /** 按名字查描述（给「我发了个表情」用）。 */
    public static String descOf(String stemOrFile) {
        if (stemOrFile == null || stemOrFile.isEmpty()) {
            return null;
        }
        String stem = stemOrFile;
        int dot = stem.lastIndexOf('.');
        if (dot > 0) {
            stem = stem.substring(0, dot);
        }
        for (String[] row : DESC) {
            if (row.length >= 3 && row[0].equals(stem)) {
                return row[2];
            }
        }
        return null;
    }

    // ---------- 用户表情的描述（视觉模型生成，或手动命名） ----------

    private static final String DESC_FILE = "sticker_names.json";
    private static java.util.Map<String, String> userDesc;

    private static java.util.Map<String, String> loadUserDesc(Context c) {
        if (userDesc != null) {
            return userDesc;
        }
        userDesc = new java.util.HashMap<String, String>();
        try {
            java.io.File f = new java.io.File(c.getFilesDir(), DESC_FILE);
            if (f.exists()) {
                String txt = Memory.readFile(f);
                if (txt != null && !txt.trim().isEmpty()) {
                    org.json.JSONObject o = new org.json.JSONObject(txt);
                    java.util.Iterator<String> it = o.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        userDesc.put(k, o.optString(k, ""));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return userDesc;
    }

    /** 给某张用户表情记一句描述。 */
    public static synchronized void saveUserDesc(Context c, String fileName, String desc) {
        if (fileName == null || desc == null || desc.trim().isEmpty()) {
            return;
        }
        java.util.Map<String, String> m = loadUserDesc(c);
        m.put(stripExt(fileName), desc.trim());
        try {
            org.json.JSONObject o = new org.json.JSONObject();
            for (java.util.Map.Entry<String, String> e : m.entrySet()) {
                o.put(e.getKey(), e.getValue());
            }
            java.io.File f = new java.io.File(c.getFilesDir(), DESC_FILE);
            java.io.File tmp = new java.io.File(f.getAbsolutePath() + ".tmp");
            java.io.FileOutputStream out = new java.io.FileOutputStream(tmp);
            out.write(o.toString().getBytes("UTF-8"));
            out.flush();
            out.close();
            if (f.exists()) {
                f.delete();
            }
            tmp.renameTo(f);
        } catch (Throwable ignored) {
        }
    }

    /** 查某张用户表情的描述（没识别过返回 null）。 */
    public static String userDescOf(Context c, String fileName) {
        return loadUserDesc(c).get(stripExt(fileName));
    }

    private static String stripExt(String n) {
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    /** 哪些用户表情还没有描述。 */
    public static java.util.List<String> undescribed(Context c) {
        java.util.List<String> out = new java.util.ArrayList<String>();
        java.util.Map<String, String> m = loadUserDesc(c);
        for (String f : userAll(c)) {
            if (!m.containsKey(stripExt(f))) {
                out.add(f);
            }
        }
        return out;
    }
}
