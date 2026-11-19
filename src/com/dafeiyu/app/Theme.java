package com.dafeiyu.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;

/**
 * 界面主题（2026-11-13 UI v2）
 * 所有颜色、圆角、渐变都从这里出，改风格只改这一个文件。
 */
public class Theme {

    // ---------- 浅色 ----------
    public static final int BG_TOP      = 0xFFEDF1F5;
    public static final int BG_BOTTOM   = 0xFFE4EAF0;
    public static final int BAR         = 0xFFFBFCFD;
    public static final int PANEL       = 0xFFFFFFFF;
    public static final int TEXT        = 0xFF1B1F24;
    public static final int MUTED       = 0xFF5A6470;   // 原 8E959E 太浅，白底上看不清
    public static final int LINE        = 0xFFE2E6EA;
    // 你的气泡：原来是偏深的薄荷绿，字压在上面发闷。
    // 换成浅一档的绿，字更清楚。
    public static final int MINE_A      = 0xFFA8E6C0;
    public static final int MINE_B      = 0xFF8CDCB0;
    public static final int MINE_TEXT   = 0xFF0B2A18;
    public static final int THEIRS      = 0xFFFFFFFF;
    public static final int ACCENT      = 0xFF4FB3A3;
    public static final int ONLINE      = 0xFF3EC98A;
    public static final int OFFLINE     = 0xFFC4C9CF;
    public static final int SHADOW      = 0x0F17242A;
    public static final int CHIP_BG     = 0xFFF0F3F6;

    // ---------- 深色 ----------
    public static final int BG_TOP_D    = 0xFF0A0A0C;   // 冷黑灰（原 0B1B33 太蓝太亮）
    public static final int BG_BOTTOM_D = 0xFF141418;
    public static final int BAR_D       = 0xFF1C1C1E;   // 顶栏中性深灰
    public static final int PANEL_D     = 0xFF1C1C1E;
    public static final int TEXT_D      = 0xFFE9F2FF;
    public static final int MUTED_D     = 0xFFC4D2E2;   // 原 8FA6C4 在玻璃底上对比度仅 2.66
    public static final int LINE_D      = 0xFF2B4C78;
    public static final int MINE_A_D    = 0xFF2C6E9E;
    public static final int MINE_B_D    = 0xFF3A85BE;
    public static final int MINE_TEXT_D = 0xFFEAF6FF;
    public static final int THEIRS_D    = 0xFF1B3A63;
    public static final int ACCENT_D    = 0xFF6FB8FF;
    public static final int CHIP_BG_D   = 0xFF1A3A63;

    // ---------- 尺寸 ----------
    public static final int R_BUBBLE   = 18;

    // ---- iMessage 气泡配色 ----
    public static final int IM_MINE      = 0xFF0B84FF;   // 蓝
    public static final int IM_MINE_D    = 0xFF0A6ED1;   // 深色模式稍沉一点
    public static final int IM_MINE_TEXT = 0xFFFFFFFF;   // 蓝底白字
    public static final int IM_THEIRS    = 0xFFE9E9EB;   // 浅灰
    public static final int IM_THEIRS_D  = 0xFF2C2C2E;   // 深色模式灰
    public static final int IM_THEIRS_TEXT = 0xFF000000;
    public static final int IM_THEIRS_TEXT_D = 0xFFEDEDED;
    public static final int IM_RADIUS    = 18;           // iMessage 胶囊圆角
    public static final int R_SMALL    = 12;
    public static final int R_PILL     = 999;
    public static final int AVATAR     = 34;
    public static final int AVATAR_BIG = 42;

    // ---------- 皮肤 ----------
    public static final String[] SKIN_NAMES = {"天蓝", "蜜桃", "湖蓝", "葡萄", "奶油", "纸白"};
    // 每个皮肤：{渐变左, 渐变右, 文字色}
    // 第一组是天蓝 —— 你的气泡默认就是这个色
    public static final int[][] SKIN_COLORS = {
        {0xFF7EC8F5, 0xFF5AB4EE, 0xFF052236},
        {0xFFFFC8CD, 0xFFFFB0B8, 0xFF4A1420},
        {0xFFA8D8F5, 0xFF88C4F0, 0xFF0D2A42},
        {0xFFD4C4F7, 0xFFBFA8F0, 0xFF26194A},
        {0xFFFFEDC4, 0xFFFADFA0, 0xFF453411},
        {0xFFF2F2F2, 0xFFE4E4E4, 0xFF1B1F24},
    };

    // ---------- 工具 ----------
    public static boolean dark = false;  // 默认浅色

    /** 当前气泡皮肤下标（0..SKIN_NAMES.length-1）。原来只画了色块，没真用上。 */
    public static int skin = 0;

    public static int skinCount() { return SKIN_NAMES.length; }

    public static int skinA() { return SKIN_COLORS[clampSkin(skin)][0]; }

    public static int skinB() { return SKIN_COLORS[clampSkin(skin)][1]; }

    public static int skinText() { return SKIN_COLORS[clampSkin(skin)][2]; }

    private static int clampSkin(int i) {
        if (i < 0) return 0;
        if (i >= SKIN_COLORS.length) return SKIN_COLORS.length - 1;
        return i;
    }

    public static int bgTop()     { return dark ? BG_TOP_D : BG_TOP; }
    public static int bgBottom()  { return dark ? BG_BOTTOM_D : BG_BOTTOM; }
    public static int bar()       { return dark ? BAR_D : BAR; }
    public static int panel()     { return dark ? PANEL_D : PANEL; }
    public static int text()      { return dark ? TEXT_D : TEXT; }
    public static int muted()     { return dark ? MUTED_D : MUTED; }
    public static int line()      { return dark ? LINE_D : LINE; }
    public static int theirs()    { return dark ? THEIRS_D : THEIRS; }
    public static int accent()    { return dark ? ACCENT_D : ACCENT; }
    public static int chipBg()    { return dark ? CHIP_BG_D : CHIP_BG; }
    public static int mineA()     { return dark ? MINE_A_D : MINE_A; }
    public static int mineB()     { return dark ? MINE_B_D : MINE_B; }
    public static int mineText() { return skinText(); }
    public static int bg()         { return dark ? BG_TOP_D : BG_TOP; }
    public static int imMine()     { return dark ? IM_MINE_D : IM_MINE; }

    // 历史记录里「我」的气泡：用「晴空」那套配色（8EC9F0 -> 66AEE8）
    public static final int ARCHIVE_MINE_LIGHT = 0xFF8EC9F0;
    public static final int ARCHIVE_MINE_DARK  = 0xFF4A9AD4;
    public static final int ARCHIVE_MINE_TEXT  = 0xFF0D2A42;

    public static int archiveMine()       { return dark ? ARCHIVE_MINE_DARK : ARCHIVE_MINE_LIGHT; }
    // 文字用黑色（深色模式下才用浅字，否则在深蓝底上看不清）
    public static int archiveMineText()   { return dark ? 0xFFFFFFFF : 0xFF000000; }
    public static int imTheirs()   { return dark ? IM_THEIRS_D : IM_THEIRS; }
    public static int imText()     { return dark ? IM_THEIRS_TEXT_D : IM_THEIRS_TEXT; }
    public static int imMineText() { return IM_MINE_TEXT; }

    /**
     * 她的气泡文字色。
     *
     * 她的气泡是白色/深灰两种，文字必须跟着气泡底走。
     * 不能用 text() —— 那是「页面文字」，深色主题下是浅色的，
     * 落在白色气泡上就看不清了。
     */
    public static int theirsText() {
        return dark ? 0xFFEDEDED : 0xFF111111;
    }

    /**
     * 毛玻璃卡片上的次要文字色。
     *
     * 卡片是半透明白 + 描边，下面透出深蓝背景，
     * 用 muted() 会糊得看不清。这里给一个对玻璃底色更响亮的值。
     */
    public static int mutedOnGlass() {
        // 原来是 0xD8E6F2（淡蓝灰），对比度 5.07 勉强够标准，
        // 但 12sp 小字叠在灰蓝玻璃上就是一片糊。
        // 提到 0xFFE4ECF4 全不透明 + 更亮，对比度 6.5 以上，小字也能看清。
        return dark ? 0xFFE4ECF4 : 0xFF3C4A5A;
    }

    /** 毛玻璃卡片上的主文字色。 */
    public static int textOnGlass() {
        return dark ? 0xFFFFFFFF : 0xFF10151C;
    }

    /** 背景渐变 */
    public static GradientDrawable background() {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{bgTop(), bgBottom()});
        return g;
    }

    /** 她的气泡（iMessage 灰，四角全圆） */
    public static GradientDrawable bubbleTheirs() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(px(IM_RADIUS));
        g.setColor(dark ? IM_THEIRS_D : IM_THEIRS);
        return g;
    }

    /** 我的气泡（跟皮肤走，四角全圆） */
    public static GradientDrawable bubbleMine() {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(px(IM_RADIUS));
        g.setColor(skinA());
        return g;
    }

    /** 胶囊（状态条、时间） */
    public static GradientDrawable pill(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(R_PILL));
        g.setColor(color);
        return g;
    }

    /** 圆角矩形描边 */
    public static GradientDrawable roundRect(int fill, int strokeColor, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(radiusDp));
        g.setColor(fill);
        if (strokeColor != 0) g.setStroke(dp1(), strokeColor);
        return g;
    }

    /** 圆形头像底 */
    public static GradientDrawable circle(int c1, int c2) {
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{c1, c2});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    /** 发送按钮 */
    public static GradientDrawable sendButton() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(dark ? IM_MINE_D : IM_MINE);
        g.setCornerRadius(px(18));
        return g;
    }

    /** 输入框底 */
    public static GradientDrawable inputBg() {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(px(20));
        g.setColor(dark ? 0xFF2C2C2E : 0xFFF2F2F7);
        g.setStroke(dp1(), dark ? 0xFF3A3A3C : 0xFFD8D8DC);
        return g;
    }

    /** 变淡一点（没有 alpha 工具时用） */
    public static int alpha(int color, float a) {
        int al = (int) (255 * a);
        return Color.argb(al, Color.red(color), Color.green(color), Color.blue(color));
    }

    // dp 转换需要 Context，这里用静态缓存
    private static float density = 0f;

    public static void init(Context ctx) {
        density = ctx.getResources().getDisplayMetrics().density;
    }
    private static int dp1() { return Math.max(1, Math.round(density)); }
    private static int px(int dp) { return Math.round(dp * density); }
}
