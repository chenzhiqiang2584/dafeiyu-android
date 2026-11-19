package com.dafeiyu.app;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.File;

/**
 * 头像缓存。
 *
 * 原版每条消息都 BitmapFactory.decodeFile 一次，50 条消息就是 50 次
 * 主线程解码 —— 滑进聊天界面明显卡。这里只解一次。
 */
public final class AvatarCache {

    private static Bitmap bmp;
    private static boolean tried = false;
    private static long stamp = -1;

    private AvatarCache() {
    }

    public static synchronized Bitmap get(Activity act) {
        File f = new File(act.getFilesDir(), "avatar.png");
        long m = f.exists() ? f.lastModified() : -1;
        if (tried && m == stamp) {
            return bmp;
        }
        tried = true;
        stamp = m;
        bmp = null;
        try {
            if (f.exists()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            }
        } catch (Throwable ignored) {
        }
        return bmp;
    }

    /** 换头像后必须调，否则一直显示旧的。 */
    public static synchronized void invalidate() {
        tried = false;
        stamp = -1;
        bmp = null;
    }

    // ---------- 你的头像 ----------

    private static Bitmap myBmp;
    private static boolean myTried = false;
    private static long myStamp = -1;

    /** 你自己的头像（my_avatar.png）。 */
    public static synchronized Bitmap getMine(android.app.Activity act) {
        File f = new File(act.getFilesDir(), "my_avatar.png");
        long m = f.exists() ? f.lastModified() : -1;
        if (myTried && m == myStamp) {
            return myBmp;
        }
        myTried = true;
        myStamp = m;
        myBmp = null;
        try {
            if (f.exists()) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 2;
                myBmp = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            }
        } catch (Throwable ignored) {
        }
        return myBmp;
    }

    /** 两个头像都清掉。 */
    public static synchronized void invalidateAll() {
        invalidate();
        myTried = false;
        myStamp = -1;
        myBmp = null;
    }
}
