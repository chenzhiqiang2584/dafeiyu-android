package com.dafeiyu.app;

import android.app.Activity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 人格库。
 *
 * 存好几个人格，随时切换，不用每次重新粘贴。
 * 文件放 Documents/大肥鱼人格/名字.md，卸载 App 也不丢。
 */
public final class PersonaLibrary {

    private static final String DIR = "大肥鱼人格";

    private PersonaLibrary() {
    }

    public static File dir() {
        try {
            File docs = android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS);
            if (docs == null) {
                return null;
            }
            File d = new File(docs, DIR);
            if (!d.exists() && !d.mkdirs()) {
                return null;
            }
            return d;
        } catch (Throwable t) {
            return null;
        }
    }

    public static List<File> list() {
        List<File> out = new ArrayList<File>();
        try {
            File d = dir();
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
                    return a.getName().compareTo(b.getName());
                }
            });
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void save(String name, String text) {
        try {
            File d = dir();
            if (d == null) {
                return;
            }
            String safe = name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
            if (safe.isEmpty()) {
                return;
            }
            if (!safe.endsWith(".md")) {
                safe += ".md";
            }
            File f = new File(d, safe);
            java.io.FileOutputStream o = new java.io.FileOutputStream(f, false);
            try {
                o.write(text.getBytes("UTF-8"));
                o.flush();
            } finally {
                try {
                    o.close();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static String read(File f) {
        try {
            return Memory.readFile(f);
        } catch (Throwable t) {
            return "";
        }
    }

    public static void delete(File f) {
        try {
            if (f != null && f.exists()) {
                f.delete();
            }
        } catch (Throwable ignored) {
        }
    }

    /** 弹出一个列表让用户选。 */
    public static void pick(final Activity act, final Chat chat) {
        try {
            List<File> items = list();
            if (items.isEmpty()) {
                // 空的时候直接给个「存当前人格」的入口，别只提示不给路
                MDialog.card(act, "\u4EBA\u683C\u5E93",
                        "\u8FD8\u6CA1\u6709\u5B58\u8FC7\u4EBA\u683C\u3002\n\n"
                                + "\u628A\u5F53\u524D\u7528\u7740\u7684\u8FD9\u4EFD\u5B58\u8FDB\u53BB\uFF0C"
                                + "\u4EE5\u540E\u53EF\u4EE5\u968F\u65F6\u5207\u56DE\u6765\u3002",
                        "\u5B58\u5F53\u524D\u4EBA\u683C",
                        () -> saveCurrent(act, chat),
                        "\u7B97\u4E86", null).show();
                return;
            }
            String[] names = new String[items.size() + 1];
            String cur = chat == null ? "" : chat.currentPersona();
            for (int i = 0; i < items.size(); i++) {
                File f = items.get(i);
                String n = f.getName().replace(".md", "");
                String txt = read(f);
                String mark = (chat != null && !cur.isEmpty() && txt.equals(cur)) ? "  ✓当前" : "";
                names[i] = n + mark;
            }
            names[items.size()] = "\uFF0B  \u5B58\u5F53\u524D\u4EBA\u683C";
            MDialog.sheet(act, "选一个人格", names, new MDialog.OnPick() {
                @Override
                public void onPick(int which) {
                    try {
                        if (which == items.size()) {
                            saveCurrent(act, chat);
                            return;
                        }
                        File f = items.get(which);
                        String txt = read(f);
                        if (txt.trim().isEmpty()) {
                            SimpleDialog.alert(act, "人格库", "这个文件是空的。");
                            return;
                        }
                        if (chat != null && chat.setPersona(txt)) {
                            SimpleDialog.alert(act, "人格库",
                                    "已切换到「" + f.getName().replace(".md", "") + "」\n"
                                            + "共 " + txt.length() + " 字");
                        } else {
                            SimpleDialog.alert(act, "人格库", "切换失败。");
                        }
                    } catch (Throwable t) {
                        SimpleDialog.alert(act, "人格库", "出错：" + t);
                    }
                }
            }).show();
        } catch (Throwable t) {
            SimpleDialog.alert(act, "人格库", "打开失败：" + t);
        }
    }

    /** 把当前人格存进库。 */
    public static void saveCurrent(final Activity act, final Chat chat) {
        if (chat == null) {
            return;
        }
        SimpleDialog.input(act, "存为新人格", "给这个人格起个名字", "我的大肥鱼",
                new SimpleDialog.OnText() {
                    @Override
                    public void onText(String s) {
                        if (s == null || s.trim().isEmpty()) {
                            return;
                        }
                        save(s.trim(), chat.currentPersona());
                        SimpleDialog.alert(act, "人格库", "已保存：" + s.trim());
                    }
                });
    }
}
