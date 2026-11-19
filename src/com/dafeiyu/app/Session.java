package com.dafeiyu.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 多会话管理。
 *
 * 每个会话有自己的 history.json、summary.md，互不干扰。
 * 会话索引存在 sessions.json：一份数组，元素是
 * {"id":"...", "title":"...", "at": 时间戳}
 */
public final class Session {

    private static final String INDEX = "sessions.json";
    private static final String K_CURRENT = "current_session";

    public static final class Item {
        public String id;
        public String title;
        public long at;

        @Override
        public String toString() {
            return title == null ? "" : title;
        }
    }

    private Session() {
    }

    private static File idxFile(Context c) {
        return new File(c.getFilesDir(), INDEX);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(MainActivity.PREF, Context.MODE_PRIVATE);
    }

    /** 当前会话 id（没有就建一个）。 */
    public static synchronized String current(Context c) {
        String id = prefs(c).getString(K_CURRENT, "");
        if (id != null && !id.isEmpty() && sessionFile(c, id, "history.json").exists()) {
            return id;
        }
        // 老版本只有一个 history.json，没有索引：迁移成第一个会话
        List<Item> list = list(c);
        if (!list.isEmpty()) {
            String first = list.get(0).id;
            setCurrent(c, first);
            return first;
        }
        // 全新的
        File legacy = new File(c.getFilesDir(), "history.json");
        String newId = newId();
        if (legacy.exists()) {
            File dst = sessionFile(c, newId, "history.json");
            try {
                dst.getParentFile().mkdirs();
                legacy.renameTo(dst);
                // 老的 summary 也带过来
                File ls = new File(c.getFilesDir(), "summary.md");
                if (ls.exists()) {
                    ls.renameTo(sessionFile(c, newId, "summary.md"));
                }
            } catch (Throwable ignored) {
            }
        }
        Item it = new Item();
        it.id = newId;
        it.title = "新聊天";
        it.at = System.currentTimeMillis();
        List<Item> all = list(c);
        all.add(0, it);
        save(c, all);
        setCurrent(c, newId);
        return newId;
    }

    public static synchronized void setCurrent(Context c, String id) {
        prefs(c).edit().putString(K_CURRENT, id).apply();
    }

    /** 新建一个会话，返回 id。 */
    public static synchronized String create(Context c, String title) {
        String id = newId();
        Item it = new Item();
        it.id = id;
        it.title = (title == null || title.trim().isEmpty()) ? "新聊天" : title.trim();
        it.at = System.currentTimeMillis();
        List<Item> all = list(c);
        all.add(0, it);
        save(c, all);
        setCurrent(c, id);
        // 建空历史文件，这样「存在」判断通过
        try {
            File f = sessionFile(c, id, "history.json");
            f.getParentFile().mkdirs();
            f.createNewFile();
        } catch (Throwable ignored) {
        }
        return id;
    }



    /** 某个会话的某个文件。 */
    public static File sessionFile(Context c, String id, String name) {
        return new File(new File(new File(c.getFilesDir(), "sessions"), id), name);
    }

    /** 会话目录。 */
    public static File dir(Context c, String id) {
        return new File(new File(c.getFilesDir(), "sessions"), id);
    }



    /** 更新某个会话的「最后活动时间」并自动起标题。 */
    public static synchronized void touch(Context c, String id, String firstUserText) {
        List<Item> all = list(c);
        for (Item it : all) {
            if (!it.id.equals(id)) {
                continue;
            }
            it.at = System.currentTimeMillis();
            // 标题还是「新聊天」时，用第一句话当标题
            if (("新聊天".equals(it.title) || it.title == null || it.title.isEmpty())
                    && firstUserText != null && !firstUserText.trim().isEmpty()) {
                String t = firstUserText.trim().replaceAll("\\s+", " ");
                if (t.length() > 12) {
                    t = t.substring(0, 12);
                }
                it.title = t;
            }
            break;
        }
        save(c, all);
    }

    /** 改会话标题。 */
    public static synchronized void rename(Context c, String id, String title) {
        List<Item> all = list(c);
        for (Item it : all) {
            if (it.id.equals(id)) {
                it.title = title == null ? "" : title.trim();
                break;
            }
        }
        save(c, all);
    }

    /** 删掉一个会话。返回新的当前会话 id（如果删的是当前的）。 */
    public static synchronized String delete(Context c, String id) {
        List<Item> all = list(c);
        List<Item> keep = new ArrayList<Item>();
        for (Item it : all) {
            if (!it.id.equals(id)) {
                keep.add(it);
            }
        }
        save(c, keep);
        // 删文件
        try {
            deleteDir(dir(c, id));
        } catch (Throwable ignored) {
        }
        String cur = prefs(c).getString(K_CURRENT, "");
        if (id.equals(cur)) {
            String next = keep.isEmpty() ? null : keep.get(0).id;
            if (next == null) {
                next = create(c, "新聊天");
            }
            setCurrent(c, next);
            return next;
        }
        return cur;
    }

    private static void deleteDir(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            File[] fs = f.listFiles();
            if (fs != null) {
                for (File k : fs) {
                    deleteDir(k);
                }
            }
        }
        f.delete();
    }

    /** 所有会话，最近的在前。 */
    public static synchronized List<Item> list(Context c) {
        List<Item> out = new ArrayList<Item>();
        try {
            File f = idxFile(c);
            if (!f.exists()) {
                return out;
            }
            String t = Memory.readFile(f);
            if (t == null || t.trim().isEmpty()) {
                return out;
            }
            JSONArray arr = new JSONArray(t);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Item it = new Item();
                it.id = o.optString("id", "");
                it.title = o.optString("title", "新聊天");
                it.at = o.optLong("at", 0);
                if (!it.id.isEmpty()) {
                    out.add(it);
                }
            }
        } catch (Throwable ignored) {
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return Long.compare(b.at, a.at);
            }
        });
        return out;
    }

    private static synchronized void save(Context c, List<Item> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Item it : list) {
                JSONObject o = new JSONObject();
                o.put("id", it.id);
                o.put("title", it.title == null ? "" : it.title);
                o.put("at", it.at);
                arr.put(o);
            }
            File f = idxFile(c);
            File tmp = new File(f.getAbsolutePath() + ".tmp");
            java.io.FileOutputStream o = new java.io.FileOutputStream(tmp);
            try {
                o.write(arr.toString().getBytes("UTF-8"));
                o.flush();
            } finally {
                try {
                    o.close();
                } catch (Throwable ignored) {
                }
            }
            if (f.exists()) {
                f.delete();
            }
            tmp.renameTo(f);
        } catch (Throwable ignored) {
        }
    }

    /** 生成一个不重复的 id。 */
    private static String newId() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
    }


}
