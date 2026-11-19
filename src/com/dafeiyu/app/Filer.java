package com.dafeiyu.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** 小工具：文件/目录复制。自写，不依赖 java.nio.file（安卓 8 以下也能用） */
final class Filer {
    private Filer() {}

    static void copyTo(File src, File dst) {
        if (src == null || !src.exists() || !src.isFile()) return;
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            File p = dst.getParentFile();
            if (p != null && !p.exists()) p.mkdirs();
            in = new FileInputStream(src);
            out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        } catch (Throwable e) {
            android.util.Log.e("FISH", "copyTo fail: " + src + " -> " + dst, e);
        } finally {
            closeQuiet(in);
            closeQuiet(out);
        }
    }

    static void copyDir(File srcDir, File dstDir) {
        if (srcDir == null || !srcDir.exists() || !srcDir.isDirectory()) return;
        if (!dstDir.exists() && !dstDir.mkdirs()) return;
        File[] kids = srcDir.listFiles();
        if (kids == null) return;
        for (File k : kids) {
            File t = new File(dstDir, k.getName());
            if (k.isDirectory()) copyDir(k, t);
            else copyTo(k, t);
        }
    }

    static void writeString(File dst, String text) {
        FileOutputStream out = null;
        try {
            File p = dst.getParentFile();
            if (p != null && !p.exists()) p.mkdirs();
            out = new FileOutputStream(dst);
            out.write(text.getBytes("UTF-8"));
            out.flush();
        } catch (Throwable ignored) {
        } finally {
            closeQuiet(out);
        }
    }

    private static void closeQuiet(java.io.Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (IOException ignored) {}
    }
}
