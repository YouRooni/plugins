package dev.rooni.player;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;

public final class PlayerConfig {

    private static final String PREFS_NAME = "md3_player_prefs";

    public static final String COLOR_COVER = "cover";
    public static final String COLOR_THEME = "theme";

    private static boolean miniPlayerDialogs = true;
    private static boolean miniPlayerTop = false;
    private static boolean miniPlayerEverywhere = false;
    private static boolean contextBarEnabled = true;
    private static String colorSource = COLOR_COVER;
    private static boolean onlineLyrics = true;
    private static boolean wavySeekBar = true;
    private static boolean lrclibAllowed = true;

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
                        .apply();
            }
        } catch (Throwable ignored) {
        }
    }

    public static void update(boolean miniDialogs, boolean contextBar, String colSource, boolean lyrics, boolean wavy, boolean miniEverywhere) {
        miniPlayerDialogs = miniDialogs;
        contextBarEnabled = contextBar;
        colorSource = colSource != null ? colSource : COLOR_COVER;
        onlineLyrics = lyrics;
        wavySeekBar = wavy;
        miniPlayerEverywhere = miniEverywhere;
        save();
    }

    public static void update(boolean miniDialogs, boolean contextBar, String colSource, boolean lyrics, boolean wavy) {
        update(miniDialogs, contextBar, colSource, lyrics, wavy, miniPlayerEverywhere);
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

    public static boolean isContextBarEnabled() {
        load();
        return contextBarEnabled;
    }

    public static String getColorSource() {
        load();
        return colorSource;
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

    public static boolean isLrclibAllowed() {
        load();
        return lrclibAllowed;
    }

    public static void setLrclibAllowed(boolean allowed) {
        lrclibAllowed = allowed;
        save();
    }
}
