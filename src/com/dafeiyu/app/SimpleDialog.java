package com.dafeiyu.app;

import android.app.Activity;

/** 极简提示框（走 MDialog，跟其他弹窗一个样式）。 */
public final class SimpleDialog {

    private SimpleDialog() {
    }

    public static void alert(Activity act, String title, String msg) {
        try {
            MDialog.card(act, title, msg, "知道了", null, null, null).show();
        } catch (Throwable ignored) {
        }
    }

    /** 带输入框的。 */
    public interface OnText {
        void onText(String s);
    }

    public static void input(Activity act, String title, String hint, String prefill,
                             final OnText cb) {
        try {
            MDialog.input(act, title, hint, prefill, new MDialog.OnInput() {
                @Override
                public void onInput(String text) {
                    if (cb != null) {
                        cb.onText(text);
                    }
                }
            }).show();
        } catch (Throwable ignored) {
        }
    }
}
