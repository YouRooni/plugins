package dev.rooni.player;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;
import org.telegram.ui.Components.FragmentContextView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

public final class PlayerHook {

    private static final List<XC_MethodHook.Unhook> unhooks = new ArrayList<>();
    private static final List<WeakReference<View>> attachedViews = new ArrayList<>();
    private static boolean active = false;
    public static volatile boolean allowClassicPlayer = false;
    private static final ThreadLocal<Integer> fcvPrevStyle = new ThreadLocal<>();

    private static final NotificationCenter.NotificationCenterDelegate ncDelegate = (id, account, args) -> {
        try {
            if (id == NotificationCenter.messagePlayingDidStart || id == NotificationCenter.messagePlayingPlayStateChanged) {
                ensureMiniPlayerAttached();
                if (id == NotificationCenter.messagePlayingDidStart) {
                    syncMusicSpeed();
                    AndroidUtilities.runOnUIThread(PlayerHook::syncMusicSpeed, 80);
                }
            }
        } catch (Throwable ignored) {
        }
    };

    public static void syncMusicSpeed() {
        try {
            MediaController mc = MediaController.getInstance();
            MessageObject mo = mc.getPlayingMessageObject();
            if (mo != null && mo.isMusic()) {
                float speed = mc.getPlaybackSpeed(true);
                if (Math.abs(speed - 1f) > 0.001f) {
                    mc.setPlaybackSpeed(true, speed);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private PlayerHook() {
    }

    public static synchronized void start() {
        if (active) return;
        active = true;
        PlayerConfig.load();

        hookPlayerAlert();
        hookFragmentContextView();
        hookNavigationLayout();

        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.addObserver(ncDelegate, NotificationCenter.messagePlayingDidStart);
            nc.addObserver(ncDelegate, NotificationCenter.messagePlayingPlayStateChanged);
        }
        ensureMiniPlayerAttached();
    }

    public static synchronized void stop() {
        if (!active) return;
        active = false;

        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.removeObserver(ncDelegate, NotificationCenter.messagePlayingDidStart);
            nc.removeObserver(ncDelegate, NotificationCenter.messagePlayingPlayStateChanged);
        }

        for (XC_MethodHook.Unhook unhook : unhooks) {
            try {
                unhook.unhook();
            } catch (Throwable ignored) {
            }
        }
        unhooks.clear();

        AndroidUtilities.runOnUIThread(() -> {
            for (WeakReference<View> ref : attachedViews) {
                View v = ref.get();
                if (v != null && v.getParent() instanceof ViewGroup) {
                    ((ViewGroup) v.getParent()).removeView(v);
                }
            }
            attachedViews.clear();

            if (PlayerSheet.instance != null) {
                try {
                    PlayerSheet.instance.dismissImmediately();
                } catch (Throwable ignored) {
                }
                PlayerSheet.instance = null;
            }
        });
    }

    public static void updateSettings(boolean miniDialogs, boolean contextBar, String colorSource, boolean onlineLyrics, boolean wavySeekBar, boolean miniEverywhere) {
        PlayerConfig.update(miniDialogs, contextBar, colorSource, onlineLyrics, wavySeekBar, miniEverywhere);
        AndroidUtilities.runOnUIThread(() -> {
            for (WeakReference<View> ref : attachedViews) {
                View v = ref.get();
                if (v instanceof PlayerMiniView) {
                    ((PlayerMiniView) v).update(true);
                } else if (v instanceof PlayerBarView) {
                    ((PlayerBarView) v).update();
                }
            }
            if (PlayerSheet.instance != null) {
                try {
                    PlayerSheet.instance.onConfigChanged();
                } catch (Throwable ignored) {
                }
            }
            ensureMiniPlayerAttached();
        });
    }

    public static void updateSettings(boolean miniDialogs, boolean contextBar, String colorSource, boolean onlineLyrics, boolean wavySeekBar) {
        updateSettings(miniDialogs, contextBar, colorSource, onlineLyrics, wavySeekBar, PlayerConfig.isMiniPlayerEverywhere());
    }

    private static void attachMiniPlayerTo(ViewGroup group, BaseFragment fragment) {
        if (group == null) return;
        PlayerMiniView mini = (PlayerMiniView) group.findViewWithTag("md3_mini_player");
        if (mini == null) {
            mini = new PlayerMiniView(group.getContext(), fragment, fragment != null ? fragment.getResourceProvider() : null);
            mini.setTag("md3_mini_player");
            group.addView(mini, LayoutHelper.createFrame(-1, -1));
            attachedViews.add(new WeakReference<>(mini));
        } else {
            mini.setCurrentFragment(fragment);
        }
        mini.bringToFront();
        mini.setFloatingAllowed(true);
        mini.update(false);
    }

    private static BaseFragment getFragment(FragmentContextView fcv) {
        try {
            Field fragmentField = FragmentContextView.class.getDeclaredField("fragment");
            fragmentField.setAccessible(true);
            return (BaseFragment) fragmentField.get(fcv);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean isMainDialogs(BaseFragment fragment) {
        if (fragment == null) return false;
        String name = fragment.getClass().getSimpleName();
        if (name.contains("DialogsActivity") || name.contains("MainTabsActivity")) {
            return true;
        }
        Bundle args = fragment.getArguments();
        return args != null && args.getBoolean("hasMainTabs", false);
    }

    private static void updateTopPanelLayout(FragmentContextView fcv, BaseFragment fragment, int targetHeightDp) {
        try {
            Field flField = FragmentContextView.class.getDeclaredField("frameLayout");
            flField.setAccessible(true);
            View fl = (View) flField.get(fcv);
            if (fl != null && fl.getLayoutParams() != null) {
                fl.getLayoutParams().height = AndroidUtilities.dp(targetHeightDp);
                fl.requestLayout();
            }
        } catch (Throwable ignored) {
        }

        ViewParent parent = fcv.getParent();
        if (parent instanceof View) {
            View pv = (View) parent;
            if (pv.getLayoutParams() != null) {
                pv.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            pv.requestLayout();
            ViewParent grand = pv.getParent();
            if (grand instanceof View) {
                View gv = (View) grand;
                if (gv.getLayoutParams() != null) {
                    gv.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                }
                gv.requestLayout();
                gv.invalidate();
            }
        }

        if (fragment != null) {
            try {
                Method invMethod = fragment.getClass().getDeclaredMethod("invalidateChatListViewTopPadding");
                invMethod.setAccessible(true);
                invMethod.invoke(fragment);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void ensureMiniPlayerAttached() {
        if (!active || !PlayerConfig.isMiniPlayerDialogs()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                LaunchActivity activity = LaunchActivity.instance;
                if (activity == null) {
                    return;
                }
                INavigationLayout layout = activity.getActionBarLayout();
                if (layout == null) {
                    return;
                }
                List<BaseFragment> fragments = new ArrayList<>();
                List<BaseFragment> stack = layout.getFragmentStack();
                if (stack != null) {
                    fragments.addAll(stack);
                }
                BaseFragment last = layout.getLastFragment();
                if (last != null && !fragments.contains(last)) {
                    fragments.add(last);
                }

                for (BaseFragment fragment : fragments) {
                    if (fragment == null || !isMainDialogs(fragment)) {
                        continue;
                    }
                    View root = fragment.getFragmentView();
                    if (root instanceof ViewGroup) {
                        attachMiniPlayerTo((ViewGroup) root, fragment);
                    }
                }
            } catch (Throwable ignored) {
            }
        });
    }

    private static void hookPlayerAlert() {

        try {
            Method showDialogMethod = BaseFragment.class.getDeclaredMethod("showDialog", Dialog.class);
            unhooks.add(XposedBridge.hookMethod(showDialogMethod, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    if (allowClassicPlayer) {
                        return;
                    }
                    if (param.args[0] instanceof AudioPlayerAlert) {
                        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
                        if (mo != null && mo.isMusic()) {
                            param.setResult(null);
                            BaseFragment fragment = (BaseFragment) param.thisObject;
                            Md3Player.open(fragment, null);
                        }
                    }
                }
            }));
        } catch (Throwable t) {

        }

        try {
            Method alertShowMethod = AudioPlayerAlert.class.getDeclaredMethod("show");
            unhooks.add(XposedBridge.hookMethod(alertShowMethod, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    if (allowClassicPlayer) {
                        return;
                    }
                    MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
                    if (mo != null && mo.isMusic()) {
                        param.setResult(null);
                        if (PlayerSheet.instance == null || !PlayerSheet.instance.isShowing()) {
                            Context ctx = ((Dialog) param.thisObject).getContext();
                            if (ctx != null) {
                                new PlayerSheet(ctx, null).show();
                            }
                        }
                    }
                }
            }));
        } catch (Throwable t) {

        }
    }

    private static void hookFragmentContextView() {
        try {
            Method checkPlayerMethod = FragmentContextView.class.getDeclaredMethod("checkPlayer", boolean.class);
            unhooks.add(XposedBridge.hookMethod(checkPlayerMethod, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    FragmentContextView fcv = (FragmentContextView) param.thisObject;
                    BaseFragment fragment = getFragment(fcv);
                    boolean isMain = isMainDialogs(fragment);

                    if (isMain) {
                        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
                        boolean isMusic = mo != null && mo.isMusic() && mo.getId() != 0;
                        if (isMusic && PlayerConfig.isMiniPlayerDialogs()) {
                            fcv.setVisibility(View.GONE);
                        }
                        PlayerBarView bar = (PlayerBarView) fcv.findViewWithTag("md3_player_bar");
                        if (bar != null) {
                            bar.setVisibility(View.GONE);
                        }
                        return;
                    }

                    if (!PlayerConfig.isContextBarEnabled()) {
                        PlayerBarView bar = (PlayerBarView) fcv.findViewWithTag("md3_player_bar");
                        if (bar != null) {
                            bar.setVisibility(View.GONE);
                        }
                        return;
                    }

                    MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
                    boolean isMusic = mo != null && mo.isMusic() && mo.getId() != 0;
                    PlayerBarView bar = (PlayerBarView) fcv.findViewWithTag("md3_player_bar");

                    if (isMusic) {
                        if (bar == null) {
                            Theme.ResourcesProvider rp = null;
                            try {
                                Field rpField = FragmentContextView.class.getDeclaredField("resourcesProvider");
                                rpField.setAccessible(true);
                                rp = (Theme.ResourcesProvider) rpField.get(fcv);
                            } catch (Throwable ignored) {
                            }

                            bar = new PlayerBarView(fcv.getContext(), rp, fcv::performClick, () -> {
                                try {
                                    Field closeField = FragmentContextView.class.getDeclaredField("closeButton");
                                    closeField.setAccessible(true);
                                    View closeBtn = (View) closeField.get(fcv);
                                    if (closeBtn != null) {
                                        closeBtn.performClick();
                                    }
                                } catch (Throwable ignored) {
                                    MediaController.getInstance().cleanupPlayer(true, true);
                                }
                            });
                            bar.setTag("md3_player_bar");
                            fcv.addView(bar, LayoutHelper.createFrame(-1, PlayerBarView.HEIGHT_DP, Gravity.TOP));
                            attachedViews.add(new WeakReference<>(bar));
                        }

                        if (fcv.getLayoutParams() != null) {
                            fcv.getLayoutParams().height = AndroidUtilities.dp(PlayerBarView.HEIGHT_DP);
                        }
                        try {
                            Field flField = FragmentContextView.class.getDeclaredField("frameLayout");
                            flField.setAccessible(true);
                            View fl = (View) flField.get(fcv);
                            if (fl != null && fl.getLayoutParams() != null) {
                                fl.getLayoutParams().height = AndroidUtilities.dp(PlayerBarView.HEIGHT_DP);
                            }
                        } catch (Throwable ignored) {
                        }
                        try {
                            fcv.setTopPadding(AndroidUtilities.dp2(PlayerBarView.HEIGHT_DP));
                        } catch (Throwable ignored) {
                        }
                        bar.setVisibility(View.VISIBLE);
                        bar.update();
                        updateTopPanelLayout(fcv, fragment, PlayerBarView.HEIGHT_DP);

                        try {
                            Field titleField = FragmentContextView.class.getDeclaredField("titleTextView");
                            titleField.setAccessible(true);
                            View title = (View) titleField.get(fcv);
                            if (title != null) title.setVisibility(View.GONE);
                        } catch (Throwable ignored) {
                        }

                        try {
                            Field subField = FragmentContextView.class.getDeclaredField("subtitleTextView");
                            subField.setAccessible(true);
                            View sub = (View) subField.get(fcv);
                            if (sub != null) sub.setVisibility(View.GONE);
                        } catch (Throwable ignored) {
                        }

                        try {
                            Field playField = FragmentContextView.class.getDeclaredField("playButton");
                            playField.setAccessible(true);
                            View play = (View) playField.get(fcv);
                            if (play != null) play.setVisibility(View.GONE);
                        } catch (Throwable ignored) {
                        }

                        try {
                            Field closeField = FragmentContextView.class.getDeclaredField("closeButton");
                            closeField.setAccessible(true);
                            View close = (View) closeField.get(fcv);
                            if (close != null) close.setVisibility(View.GONE);
                        } catch (Throwable ignored) {
                        }

                        try {
                            Field speedField = FragmentContextView.class.getDeclaredField("playbackSpeedButton");
                            speedField.setAccessible(true);
                            View speed = (View) speedField.get(fcv);
                            if (speed != null) speed.setVisibility(View.GONE);
                        } catch (Throwable ignored) {
                        }
                    } else {
                        if (bar != null) {
                            bar.setVisibility(View.GONE);
                        }

                        try {
                            Field titleField = FragmentContextView.class.getDeclaredField("titleTextView");
                            titleField.setAccessible(true);
                            View title = (View) titleField.get(fcv);
                            if (title != null) title.setVisibility(View.VISIBLE);
                        } catch (Throwable ignored) {
                        }

                        try {
                            Field subField = FragmentContextView.class.getDeclaredField("subtitleTextView");
                            subField.setAccessible(true);
                            View sub = (View) subField.get(fcv);
                            if (sub != null) sub.setVisibility(View.VISIBLE);
                        } catch (Throwable ignored) {
                        }

                        try {
                            Field playField = FragmentContextView.class.getDeclaredField("playButton");
                            playField.setAccessible(true);
                            View play = (View) playField.get(fcv);
                            if (play != null) play.setVisibility(View.VISIBLE);
                        } catch (Throwable ignored) {
                        }

                        try {
                            Field closeField = FragmentContextView.class.getDeclaredField("closeButton");
                            closeField.setAccessible(true);
                            View close = (View) closeField.get(fcv);
                            if (close != null) close.setVisibility(View.VISIBLE);
                        } catch (Throwable ignored) {
                        }

                        try {
                            Field speedField = FragmentContextView.class.getDeclaredField("playbackSpeedButton");
                            speedField.setAccessible(true);
                            View speed = (View) speedField.get(fcv);
                            if (speed != null) speed.setVisibility(View.VISIBLE);
                        } catch (Throwable ignored) {
                        }

                        try {
                            Field flField = FragmentContextView.class.getDeclaredField("frameLayout");
                            flField.setAccessible(true);
                            View fl = (View) flField.get(fcv);
                            if (fl != null && fl.getLayoutParams() != null) {
                                fl.getLayoutParams().height = AndroidUtilities.dp(36);
                                fl.requestLayout();
                            }
                        } catch (Throwable ignored) {
                        }

                        if (fcv.getLayoutParams() != null) {
                            fcv.getLayoutParams().height = AndroidUtilities.dp(36);
                        }
                        try {
                            fcv.setTopPadding(AndroidUtilities.dp2(36));
                        } catch (Throwable ignored) {
                        }
                        fcv.requestLayout();
                        updateTopPanelLayout(fcv, fragment, 36);
                    }
                }
            }));
        } catch (Throwable t) {

        }

        try {
            Method getStyleHeight = FragmentContextView.class.getDeclaredMethod("getStyleHeight");
            unhooks.add(XposedBridge.hookMethod(getStyleHeight, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    FragmentContextView fcv = (FragmentContextView) param.thisObject;
                    BaseFragment fragment = getFragment(fcv);
                    if (!isMainDialogs(fragment) && PlayerConfig.isContextBarEnabled()) {
                        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
                        if (mo != null && mo.isMusic() && mo.getId() != 0) {
                            param.setResult(PlayerBarView.HEIGHT_DP);
                        }
                    }
                }
            }));
        } catch (Throwable ignored) {
        }

        try {
            Method dispatchDraw = FragmentContextView.class.getDeclaredMethod("dispatchDraw", android.graphics.Canvas.class);
            Field currentStyleField = FragmentContextView.class.getDeclaredField("currentStyle");
            currentStyleField.setAccessible(true);
            unhooks.add(XposedBridge.hookMethod(dispatchDraw, new XC_MethodHook() {
                @Override
                public void beforeHookedMethod(MethodHookParam param) {
                    try {
                        FragmentContextView fcv = (FragmentContextView) param.thisObject;
                        BaseFragment fragment = getFragment(fcv);
                        if (!isMainDialogs(fragment) && PlayerConfig.isContextBarEnabled()) {
                            MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
                            if (mo != null && mo.isMusic() && mo.getId() != 0) {
                                int style = currentStyleField.getInt(fcv);
                                if (style == 0) {
                                    fcvPrevStyle.set(style);
                                    currentStyleField.setInt(fcv, -1);
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }

                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    try {
                        Integer prev = fcvPrevStyle.get();
                        if (prev != null) {
                            fcvPrevStyle.remove();
                            FragmentContextView fcv = (FragmentContextView) param.thisObject;
                            currentStyleField.setInt(fcv, prev);
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }));
        } catch (Throwable ignored) {
        }
    }

    private static void hookNavigationLayout() {

        try {
            for (Method m : ActionBarLayout.class.getDeclaredMethods()) {
                String name = m.getName();
                if ("presentFragment".equals(name) || "closeLastFragment".equals(name) || "showLastFragment".equals(name) || "rebuildAllFragmentViews".equals(name)) {
                    try {
                        unhooks.add(XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            public void afterHookedMethod(MethodHookParam param) {
                                ensureMiniPlayerAttached();
                                AndroidUtilities.runOnUIThread(PlayerHook::ensureMiniPlayerAttached, 150);
                            }
                        }));
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        try {
            Class<?> mainTabsClass = Class.forName("org.telegram.ui.MainTabsActivity");
            Method createView = mainTabsClass.getDeclaredMethod("createView", Context.class);
            unhooks.add(XposedBridge.hookMethod(createView, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    if (!PlayerConfig.isMiniPlayerDialogs()) return;
                    View root = (View) param.getResult();
                    if (root instanceof ViewGroup) {
                        BaseFragment fragment = (BaseFragment) param.thisObject;
                        attachMiniPlayerTo((ViewGroup) root, fragment);
                    }
                }
            }));
        } catch (Throwable ignored) {
        }

        try {
            Method createView = DialogsActivity.class.getDeclaredMethod("createView", Context.class);
            unhooks.add(XposedBridge.hookMethod(createView, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    if (!PlayerConfig.isMiniPlayerDialogs()) return;
                    BaseFragment fragment = (BaseFragment) param.thisObject;
                    Bundle args = fragment.getArguments();
                    if (args != null && args.getBoolean("hasMainTabs", false)) {
                        return;
                    }
                    View root = (View) param.getResult();
                    if (root instanceof ViewGroup) {
                        attachMiniPlayerTo((ViewGroup) root, fragment);
                    }
                }
            }));
        } catch (Throwable ignored) {
        }

        try {
            Method m = LaunchActivity.class.getDeclaredMethod("onResume");
            unhooks.add(XposedBridge.hookMethod(m, new XC_MethodHook() {
                @Override
                public void afterHookedMethod(MethodHookParam param) {
                    ensureMiniPlayerAttached();
                }
            }));
        } catch (Throwable ignored) {
        }
    }
}
