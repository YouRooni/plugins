package dev.rooni.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

public final class PlayerConfig {

    private static final String PREFS_NAME = "md3_player_prefs";

    public static final String COLOR_COVER = "cover";
    public static final String COLOR_THEME = "theme";

    public static final int ANIM_MORPH = 0;
    public static final int ANIM_SLIDE = 1;

    private static boolean miniPlayerDialogs = true;
    private static boolean miniPlayerTop = false;
    private static boolean miniPlayerEverywhere = false;
    private static boolean contextBarEnabled = true;
    private static String colorSource = COLOR_COVER;
    private static boolean onlineLyrics = true;
    private static boolean wavySeekBar = true;
    private static boolean lrclibAllowed = true;
    private static int animStyle = ANIM_MORPH;
    private static boolean seekBarDot = true;

    private static boolean loaded = false;

    private PlayerConfig() {
    }

    public static void load() {
        if (loaded) return;
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx != null) {
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                miniPlayerDialogs = prefs.getBoolean("mini_player_dialogs", true);
                miniPlayerTop = prefs.getBoolean("mini_player_top", false);
                miniPlayerEverywhere = prefs.getBoolean("mini_player_everywhere", false);
                contextBarEnabled = prefs.getBoolean("context_bar_enabled", true);
                colorSource = prefs.getString("color_source", COLOR_COVER);
                onlineLyrics = prefs.getBoolean("online_lyrics", true);
                wavySeekBar = prefs.getBoolean("wavy_seekbar", true);
                lrclibAllowed = prefs.getBoolean("lrclib_allowed", true);
                animStyle = prefs.getInt("anim_style", ANIM_MORPH);
                seekBarDot = prefs.getBoolean("seekbar_dot", true);
                loaded = true;
            }
        } catch (Throwable ignored) {
        }
    }

    public static void save() {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx != null) {
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                prefs.edit()
                        .putBoolean("mini_player_dialogs", miniPlayerDialogs)
                        .putBoolean("mini_player_top", miniPlayerTop)
                        .putBoolean("mini_player_everywhere", miniPlayerEverywhere)
                        .putBoolean("context_bar_enabled", contextBarEnabled)
                        .putString("color_source", colorSource)
                        .putBoolean("online_lyrics", onlineLyrics)
                        .putBoolean("wavy_seekbar", wavySeekBar)
                        .putBoolean("lrclib_allowed", lrclibAllowed)
                        .putInt("anim_style", animStyle)
                        .putBoolean("seekbar_dot", seekBarDot)
                        .apply();
            }
        } catch (Throwable ignored) {
        }
    }

    public static void update(boolean miniDialogs, boolean contextBar, String colSource, boolean lyrics, boolean wavy, boolean miniEverywhere, int anim) {
        loaded = true;
        miniPlayerDialogs = miniDialogs;
        contextBarEnabled = contextBar;
        colorSource = colSource != null ? colSource : COLOR_COVER;
        onlineLyrics = lyrics;
        wavySeekBar = wavy;
        miniPlayerEverywhere = miniEverywhere;
        animStyle = anim;
        save();
    }

    public static void setAnimStyle(int style) {
        loaded = true;
        animStyle = style;
        save();
        try {
            android.util.Log.e("MD3Player", "PlayerConfig.setAnimStyle -> " + style);
        } catch (Throwable ignored) {
        }
    }

    public static boolean isSlideAnimation() {
        load();
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx != null) {
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                animStyle = prefs.getInt("anim_style", animStyle);
            }
        } catch (Throwable ignored) {
        }
        try {
            android.util.Log.e("MD3Player", "isSlideAnimation checked -> " + (animStyle == ANIM_SLIDE) + " (animStyle=" + animStyle + ")");
        } catch (Throwable ignored) {
        }
        return animStyle == ANIM_SLIDE;
    }

    public static int getAnimStyle() {
        load();
        return animStyle;
    }

    public static boolean isMiniPlayerDialogs() {
        load();
        return miniPlayerDialogs;
    }

    public static boolean isMiniPlayerTop() {
        load();
        return miniPlayerTop;
    }

    public static void setMiniPlayerTop(boolean top) {
        miniPlayerTop = top;
        save();
    }

    public static boolean isMiniPlayerEverywhere() {
        load();
        return miniPlayerEverywhere;
    }

    public static void setMiniPlayerEverywhere(boolean everywhere) {
        miniPlayerEverywhere = everywhere;
        save();
    }

    public static void setMiniPlayerDialogs(boolean dialogs) {
        loaded = true;
        miniPlayerDialogs = dialogs;
        save();
    }

    public static boolean isContextBarEnabled() {
        load();
        return contextBarEnabled;
    }

    public static void setContextBarEnabled(boolean enabled) {
        loaded = true;
        contextBarEnabled = enabled;
        save();
    }

    public static String getColorSource() {
        load();
        return colorSource;
    }

    public static void setColorSource(String source) {
        loaded = true;
        colorSource = source != null ? source : COLOR_COVER;
        save();
    }

    public static boolean isColorFromCover() {
        load();
        return COLOR_COVER.equals(colorSource);
    }

    public static boolean isOnlineLyrics() {
        load();
        return onlineLyrics;
    }

    public static boolean isWavySeekBar() {
        load();
        return wavySeekBar;
    }

    public static void setWavySeekBar(boolean wavy) {
        loaded = true;
        wavySeekBar = wavy;
        save();
    }

    public static boolean isLrclibAllowed() {
        load();
        return lrclibAllowed;
    }

    public static void setLrclibAllowed(boolean allowed) {
        lrclibAllowed = allowed;
        save();
    }

    public static boolean isSeekBarDot() {
        load();
        try {
            Context ctx = ApplicationLoader.applicationContext;
            if (ctx != null) {
                SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                seekBarDot = prefs.getBoolean("seekbar_dot", seekBarDot);
            }
        } catch (Throwable ignored) {
        }
        return seekBarDot;
    }

    public static void setSeekBarDot(boolean dot) {
        loaded = true;
        seekBarDot = dot;
        save();
    }
}
