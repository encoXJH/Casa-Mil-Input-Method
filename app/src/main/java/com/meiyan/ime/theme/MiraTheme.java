package com.meiyan.ime.theme;

public final class MiraTheme {
    public static final int MIRA = 0;
    public static final int DUNHUANG = 1;
    public static final int MORANDI = 2;

    public static int index = MIRA;
    public static boolean freqColor = true;

    public static int KB_BG;
    public static int KEY;
    public static int KEY_TEXT;
    public static int FUNC;
    public static int FUNC_TEXT;
    public static int ACCENT;
    public static int ACCENT_TEXT;
    public static int ICON_BG;
    public static int ICON_TEXT;
    public static int CARD;
    public static int CARD_INK;
    public static int CARD_SUB;
    public static int CARD_HI;
    public static int CARD_CELL;
    public static int STROKE;
    public static int PAGE_BG;
    public static int PAGE_SURFACE;
    public static int PAGE_TITLE;
    public static int PAGE_INK;
    public static int PAGE_SUB;
    public static int PAGE_OK;
    public static int PAGE_ERR;
    public static int PAGE_LINE;
    public static int PAGE_CHIP;
    public static int RW_GLASS;
    public static int RW_BORDER;
    public static int RW_INK;
    public static int RW_BTN_BG;
    public static int RW_BTN_INK;
    public static int RW_BTN_ON;
    public static int RW_BTN_ON_I;
    public static int BUBBLE_BG;
    public static int BUBBLE_TEXT;
    public static int SHIFT_TEMP_BG;
    public static int SHIFT_TEMP_I;
    public static int PEN;
    public static int FREQ_LOW;
    public static int FREQ_MID;
    public static int FREQ_HIGH;

    public static int maxTypingCount = 12;

    private static final double[] EN_FREQ = {
            8.2, 1.5, 2.8, 4.3, 12.7, 2.2, 2.0, 6.1, 7.0, 0.15,
            0.8, 4.0, 2.4, 6.7, 7.5, 1.9, 0.10, 6.0, 6.3, 9.1,
            2.8, 1.0, 2.4, 0.15, 2.0, 0.07
    };

    private MiraTheme() {
    }

    public static String name(int i) {
        if (i == DUNHUANG) return "敦煌";
        if (i == MORANDI) return "莫兰迪";
        return "米拉之家";
    }

    public static void applyTheme(int i) {
        index = i;
        if (i == DUNHUANG) {
            KB_BG = 0xFFEFE0C4;
            KEY = 0xFFEAD2A6;
            KEY_TEXT = 0xFF4A2E1E;
            FUNC = 0xFFE2C89B;
            FUNC_TEXT = 0xFF2E6B62;
            ACCENT = 0xFF9E3B24;
            ACCENT_TEXT = 0xFFFFF2E0;
            ICON_BG = 0xFF2E6B62;
            ICON_TEXT = 0xFFF3E4C8;
            CARD = 0xFFF8EED8;
            CARD_INK = 0xFF4A2E1E;
            CARD_SUB = 0xFF9A7B57;
            CARD_HI = 0xFFF1DFC0;
            CARD_CELL = 0xFFFFF8EA;
            STROKE = 0xFFD9BE8D;
            PAGE_BG = 0xFFF4E8CF;
            PAGE_SURFACE = 0xFFFBF3E3;
            PAGE_TITLE = 0xFF2E6B62;
            PAGE_INK = 0xFF4A2E1E;
            PAGE_SUB = 0xFF9A7B57;
            PAGE_OK = 0xFF2E6B62;
            PAGE_ERR = 0xFF9E3B24;
            PAGE_LINE = 0xFFE2CDA6;
            PAGE_CHIP = 0xFFEFDFC1;
            RW_GLASS = 0xFFF6ECD8;
            RW_BORDER = 0xFFD8BD8B;
            RW_INK = 0xFF4A2E1E;
            RW_BTN_BG = 0xFFEAD2A6;
            RW_BTN_INK = 0xFF4A2E1E;
            RW_BTN_ON = 0xFF9E3B24;
            RW_BTN_ON_I = 0xFFFFF2E0;
            BUBBLE_BG = 0xFF2E6B62;
            BUBBLE_TEXT = 0xFFF6ECD8;
            SHIFT_TEMP_BG = 0xFFC9733F;
            SHIFT_TEMP_I = 0xFFFFF2E0;
            PEN = 0xFF9E3B24;
            FREQ_LOW = 0xFFEAD2A6;
            FREQ_MID = 0xFFC9733F;
            FREQ_HIGH = 0xFF9E3B24;
        } else if (i == MORANDI) {
            KB_BG = 0xFFE8E6E1;
            KEY = 0xFFE2DFDA;
            KEY_TEXT = 0xFF4C4A46;
            FUNC = 0xFFDAD6D0;
            FUNC_TEXT = 0xFF5E6B63;
            ACCENT = 0xFF7D8C94;
            ACCENT_TEXT = 0xFFFFFFFF;
            ICON_BG = 0xFF7D8C94;
            ICON_TEXT = 0xFFF2F0EC;
            CARD = 0xFFF4F2EE;
            CARD_INK = 0xFF4C4A46;
            CARD_SUB = 0xFF8F8B84;
            CARD_HI = 0xFFE8E3DC;
            CARD_CELL = 0xFFFBFAF8;
            STROKE = 0xFFD3CFC8;
            PAGE_BG = 0xFFEFEDE9;
            PAGE_SURFACE = 0xFFF8F6F3;
            PAGE_TITLE = 0xFF5E6B63;
            PAGE_INK = 0xFF4C4A46;
            PAGE_SUB = 0xFF8F8B84;
            PAGE_OK = 0xFF6F8574;
            PAGE_ERR = 0xFFA06A5E;
            PAGE_LINE = 0xFFDBD7D0;
            PAGE_CHIP = 0xFFE5E1DA;
            RW_GLASS = 0xFFF2F0EC;
            RW_BORDER = 0xFFD3CFC8;
            RW_INK = 0xFF4C4A46;
            RW_BTN_BG = 0xFFE2DFDA;
            RW_BTN_INK = 0xFF4C4A46;
            RW_BTN_ON = 0xFF7D8C94;
            RW_BTN_ON_I = 0xFFFFFFFF;
            BUBBLE_BG = 0xFF5E6B63;
            BUBBLE_TEXT = 0xFFF2F0EC;
            SHIFT_TEMP_BG = 0xFFA8B2A6;
            SHIFT_TEMP_I = 0xFFFFFFFF;
            PEN = 0xFF7D8C94;
            FREQ_LOW = 0xFFE2DFDA;
            FREQ_MID = 0xFFABA79F;
            FREQ_HIGH = 0xFF8C7A6E;
        } else {
            KB_BG = 0xFFF1EADB;
            KEY = 0xFFEDE2CD;
            KEY_TEXT = 0xFF3E5149;
            FUNC = 0xFFE4D8C2;
            FUNC_TEXT = 0xFF3E5149;
            ACCENT = 0xFFC27242;
            ACCENT_TEXT = 0xFFFFFFFF;
            ICON_BG = 0xFF3E5149;
            ICON_TEXT = 0xFFF1EADB;
            CARD = 0xFFFBF6EC;
            CARD_INK = 0xFF3E5149;
            CARD_SUB = 0xFF8A8175;
            CARD_HI = 0xFFF3E6D0;
            CARD_CELL = 0xFFFFFFFF;
            STROKE = 0xFFD8CBB2;
            PAGE_BG = 0xFFF5F0E6;
            PAGE_SURFACE = 0xFFFBF7EF;
            PAGE_TITLE = 0xFF3E5149;
            PAGE_INK = 0xFF3A3A34;
            PAGE_SUB = 0xFF8A8175;
            PAGE_OK = 0xFF4F7A5C;
            PAGE_ERR = 0xFFB4553F;
            PAGE_LINE = 0xFFE1D6C2;
            PAGE_CHIP = 0xFFEFE4CF;
            RW_GLASS = 0xFFF7F1E5;
            RW_BORDER = 0xFFD9C9AE;
            RW_INK = 0xFF3E5149;
            RW_BTN_BG = 0xFFEDE2CD;
            RW_BTN_INK = 0xFF3E5149;
            RW_BTN_ON = 0xFFC27242;
            RW_BTN_ON_I = 0xFFFFFFFF;
            BUBBLE_BG = 0xFF3E5149;
            BUBBLE_TEXT = 0xFFF7F1E5;
            SHIFT_TEMP_BG = 0xFFD9A66B;
            SHIFT_TEMP_I = 0xFF3E5149;
            PEN = 0xFF3E5149;
            FREQ_LOW = 0xFFEDE2CD;
            FREQ_MID = 0xFFD9A66B;
            FREQ_HIGH = 0xFFC27242;
        }
    }

    public static int freqColorFor(int typingCount, int fallbackIndex) {
        if (!freqColor) return KEY;
        double t;
        if (typingCount <= 0) {
            int idx = fallbackIndex - 'a';
            if (idx < 0 || idx >= EN_FREQ.length) idx = Math.abs(fallbackIndex) % EN_FREQ.length;
            double f = EN_FREQ[idx] / 12.7;
            t = Math.pow(f, 1.55);
        } else {
            double max = Math.max(12, maxTypingCount);
            double raw = Math.log1p(typingCount) / Math.log1p(max);
            t = Math.pow(Math.min(1.0, Math.max(0.0, raw)), 1.55);
        }
        return blend(FREQ_LOW, FREQ_HIGH, t);
    }

    public static int blend(int c1, int c2, double t) {
        if (t <= 0) return c1;
        if (t >= 1) return c2;
        int a1 = (c1 >> 24) & 0xFF, r1 = (c1 >> 16) & 0xFF, g1 = (c1 >> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >> 24) & 0xFF, r2 = (c2 >> 16) & 0xFF, g2 = (c2 >> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * t);
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}