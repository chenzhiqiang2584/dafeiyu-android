package com.dafeiyu.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 表情包面板 —— 照老版 StickerPanel 的样子。
 *
 * 图都打在 assets/stickers/ 里，不用联网。
 * 点一张就发出去。
 */
public class StickerPanel {

    public interface OnPick { void onPick(String asset); }
    public interface OnAdd { void onAdd(); }

    private final Activity act;
    private final OnPick cb;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private LinearLayout sheet;
    private GridLayout grid;
    private String[] names = new String[0];
    private String[] userNames = new String[0];
    private boolean added = false;
    private OnAdd addListener;

    public StickerPanel(Activity a, OnPick cb) {
        this.act = a;
        this.cb = cb;
    }

    public void setOnAdd(OnAdd l) { this.addListener = l; }

    private int dp(int v) {
        return (int) (v * act.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 必须先 addView 到界面上，再 show() */
    public View getView() {
        if (root != null) return root;

        root = new FrameLayout(act);
        root.setVisibility(View.GONE);

        // 半透明遮罩，点一下关掉
        View mask = new View(act);
        mask.setBackgroundColor(0x66000000);
        mask.setOnClickListener(v -> hide(true));
        root.addView(mask, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        sheet = new LinearLayout(act);
        sheet.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.dark ? 0xFF22262D : 0xFFFFFFFF);
        bg.setCornerRadii(new float[]{dp(20), dp(20), dp(20), dp(20), 0, 0, 0, 0});
        sheet.setBackground(bg);
        sheet.setPadding(dp(12), dp(10), dp(12), dp(12));

        // 顶部小把手 + 标题
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(act);
        title.setText("表情包");
        title.setTextSize(15);
        title.setTextColor(Theme.text());
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(act);
        close.setText("收起");
        close.setTextSize(13);
        close.setTextColor(Theme.accent());
        close.setPadding(dp(8), dp(4), dp(4), dp(4));
        close.setOnClickListener(v -> hide(true));
        head.addView(close);

        sheet.addView(head);

        // 网格
        ScrollView sc = new ScrollView(act);
        grid = new GridLayout(act);
        grid.setColumnCount(4);
        grid.setPadding(0, dp(8), 0, 0);
        sc.addView(grid, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sheet.addView(sc, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(320)));

        // 「＋ 从相册添加」
        TextView addBtn = new TextView(act);
        addBtn.setText("＋ 从相册添加");
        addBtn.setTextSize(14);
        addBtn.setGravity(Gravity.CENTER);
        addBtn.setTextColor(Theme.accent());
        GradientDrawable ag = new GradientDrawable();
        ag.setCornerRadius(dp(10));
        ag.setColor(Theme.dark ? 0xFF2A3038 : 0xFFF0F3F7);
        addBtn.setBackground(ag);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        alp.topMargin = dp(8);
        addBtn.setOnClickListener(v -> {
            if (addListener != null) addListener.onAdd();
        });
        sheet.addView(addBtn, alp);

        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.gravity = Gravity.BOTTOM;
        root.addView(sheet, slp);

        // 异步读目录 + 填格子
        new Thread(() -> {
            final String[] assets = listAssets();
            final String[] users = listUser();
            ui.post(() -> {
                names = assets;
                userNames = users;
                buildGrid(grid);
            });
        }).start();

        return root;
    }

    /**
     * 用户自己加的表情目录。
     *
     * 原来在 files/stickers（应用私有），卸载就没了，也不方便往里放图。
     * 改到 Documents/大肥鱼表情/ —— 用文件管理器直接能把图拷进去，
     * 卸载重装也还在。
     */
    public static java.io.File userDir(Activity a) {
        try {
            java.io.File docs = android.os.Environment
                    .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS);
            if (docs != null) {
                java.io.File d = new java.io.File(docs, "大肥鱼表情");
                if (!d.exists() && !d.mkdirs()) {
                    // 建不出来就退回私有目录
                }
                if (d.exists() && d.canWrite()) {
                    return d;
                }
            }
        } catch (Throwable ignored) {
        }
        java.io.File d = new java.io.File(a.getFilesDir(), "stickers");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private String[] listAssets() {
        try {
            String[] all = act.getAssets().list("stickers");
            if (all == null) return new String[0];
            List<String> out = new ArrayList<>();
            for (String n : all) {
                String l = n.toLowerCase();
                if (l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".gif")) out.add(n);
            }
            return out.toArray(new String[0]);
        } catch (Exception e) {
            return new String[0];
        }
    }

    private String[] listUser() {
        try {
            java.io.File[] fs = userDir(act).listFiles();
            if (fs == null) return new String[0];
            List<String> out = new ArrayList<>();
            for (java.io.File f : fs) {
                String l = f.getName().toLowerCase();
                if (l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".gif"))
                    out.add(f.getName());
            }
            return out.toArray(new String[0]);
        } catch (Exception e) {
            return new String[0];
        }
    }

    /** 刷新面板（导入完调用） */
    public void refresh() {
        if (grid == null) return;
        new Thread(() -> {
            final String[] assets = listAssets();
            final String[] users = listUser();
            ui.post(() -> {
                names = assets;
                userNames = users;
                buildGrid(grid);
            });
        }).start();
    }

    private void buildGrid(GridLayout grid) {
        grid.removeAllViews();

        // 内置的（assets/stickers/）+ 用户自己加的，全部平铺在一起，不分标题
        java.util.List<String> allUser = new java.util.ArrayList<String>();
        if (userNames != null) {
            for (String n : userNames) {
                allUser.add(n);
            }
        }
        java.util.List<String> allBuiltin = new java.util.ArrayList<String>();
        if (names != null) {
            for (String n : names) {
                allBuiltin.add(n);
            }
        }

        if (allUser.isEmpty() && allBuiltin.isEmpty()) {
            TextView t = new TextView(act);
            t.setText("还没有表情。\n\n"
                    + "① 点下面「＋ 从相册添加」，选几张图\n"
                    + "② 或者用文件管理器把图丢进\n"
                    + "    Documents/大肥鱼表情/\n"
                    + "    （支持 png / jpg / gif）");
            t.setTextSize(13);
            t.setTextColor(Theme.muted());
            t.setLineSpacing(0f, 1.4f);
            t.setPadding(dp(6), dp(20), 0, dp(10));
            grid.addView(t, spanFull());
            return;
        }

        // 先内置，再用户自己加的
        for (String n : allBuiltin) {
            addCell(grid, n, false);
        }
        for (String n : allUser) {
            addCell(grid, n, true);
        }
    }

    private GridLayout.LayoutParams spanFull() {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.columnSpec = GridLayout.spec(0, 4, 1f);
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        return lp;
    }

    private TextView headerRow(String s) {
        TextView tv = new TextView(act);
        tv.setText(s);
        tv.setTextSize(11.5f);
        tv.setTextColor(Theme.muted());
        tv.setPadding(dp(4), dp(10), 0, dp(4));
        return tv;
    }

    private void addCell(GridLayout grid, String name, boolean user) {
        ImageView iv = new ImageView(act);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(10));
        g.setColor(Theme.dark ? 0xFF2A3038 : 0xFFF0F3F7);
        iv.setBackground(g);

        int cell = (act.getResources().getDisplayMetrics().widthPixels - dp(24) - dp(9) * 3) / 4;
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = cell;
        lp.height = cell;
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        grid.addView(iv, lp);

        // 一张一张按需加载，不然几百张一起解码会炸
        new Thread(() -> {
            Bitmap bmp = user ? loadUserBmp(name) : loadBmp(name);
            if (bmp != null) ui.post(() -> iv.setImageBitmap(bmp));
        }).start();

        iv.setOnClickListener(v -> {
            hide();
            cb.onPick(user ? ("user/" + name) : ("stickers/" + name));
        });

        // 长按删除（只对我加的生效）
        if (user) {
            iv.setOnLongClickListener(v -> {
                MDialog.card(act, "删掉这张表情？", null,
                            "删", () -> {
                                new java.io.File(userDir(act), name).delete();
                                refresh();
                            }, "算了", null).show();
                return true;
            });
        }
    }

    private Bitmap loadUserBmp(String name) {
        java.io.File f = new java.io.File(userDir(act), name);
        // GIF 用 Movie 取第一帧，BitmapFactory 解不出来
        if (name.toLowerCase().endsWith(".gif")) {
            try {
                java.io.FileInputStream in = new java.io.FileInputStream(f);
                android.graphics.Movie m = android.graphics.Movie.decodeStream(in);
                in.close();
                if (m != null) {
                    int w = Math.max(1, m.width());
                    int h = Math.max(1, m.height());
                    Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    android.graphics.Canvas cv = new android.graphics.Canvas(b);
                    m.setTime(0);
                    m.draw(cv, 0, 0);
                    return b;
                }
            } catch (Throwable ignored) {}
        }
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), o);
        } catch (Throwable e) {
            return null;
        }
    }

    private Bitmap loadBmp(String name) {
        try {
            InputStream in = act.getAssets().open("stickers/" + name);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 2;          // 缩一半，够看
            Bitmap b = BitmapFactory.decodeStream(in, null, o);
            in.close();
            return b;
        } catch (Throwable e) {
            return null;
        }
    }

    /**
     * 用 PopupWindow 弹出，窗口层级比输入法高 ——
     * 这样不管键盘收不收得掉，表情面板一定盖在上面。
     */
    public void show() {
        if (root == null) return;

        if (popup == null) {
            popup = new android.widget.PopupWindow(act);
            popup.setContentView(root);
            popup.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
            popup.setHeight(ViewGroup.LayoutParams.MATCH_PARENT);
            popup.setFocusable(true);
            popup.setTouchable(true);
            popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0x00000000));
            popup.setOutsideTouchable(true);
            popup.setInputMethodMode(android.widget.PopupWindow.INPUT_METHOD_NEEDED);
            try { popup.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE); }
            catch (Throwable ignored) {}
            popup.setOnDismissListener(() -> {
                if (wantKeyboard && afterHide != null) afterHide.run();
            });
        }

        root.setVisibility(View.VISIBLE);
        if (!popup.isShowing()) {
            try {
                popup.showAtLocation(act.getWindow().getDecorView(),
                        Gravity.BOTTOM, 0, 0);
            } catch (Throwable e) {
                // 兜底：塞回原来的 FrameLayout
                try {
                    ViewGroup parent = (ViewGroup) act.getWindow().getDecorView();
                    if (root.getParent() == null && parent != null) {
                        parent.addView(root, new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
                    }
                    root.setVisibility(View.VISIBLE);
                } catch (Throwable ignored) {}
            }
        }
        root.setAlpha(0f);
        root.animate().alpha(1f).setDuration(160).start();
        sheet.setTranslationY(dp(40));
        sheet.animate().translationY(0).setDuration(200).start();
    }

    private android.widget.PopupWindow popup;

    public void hide() { hide(false); }

    /** @param backToKeyboard 收起后要不要把键盘叫回来（点「收起」或点遮罩时用） */
    public void hide(boolean backToKeyboard) {
        if (root == null) return;
        wantKeyboard = backToKeyboard;
        root.animate().alpha(0f).setDuration(140)
                .withEndAction(() -> {
                    root.setVisibility(View.GONE);
                    if (popup != null && popup.isShowing()) {
                        try { popup.dismiss(); } catch (Throwable ignored) {}
                    } else if (backToKeyboard && afterHide != null) {
                        afterHide.run();
                    }
                }).start();
    }

    private boolean wantKeyboard = false;

    private Runnable afterHide;

    public void setAfterHide(Runnable r) { this.afterHide = r; }

    public boolean isShown() {
        return root != null && root.getVisibility() == View.VISIBLE;
    }
}
