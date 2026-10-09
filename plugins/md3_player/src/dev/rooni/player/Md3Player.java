package dev.rooni.player;

import android.content.Context;
import android.os.Bundle;

import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AudioPlayerAlert;

import dev.rooni.player.PlayerConfig;

public final class Md3Player {

    private Md3Player() {
    }

    public static boolean enabled() {
        return true;
    }

    public static boolean miniEnabled() {
        return PlayerConfig.isMiniPlayerDialogs() || PlayerConfig.isMiniPlayerEverywhere();
    }

    public static boolean contextBarEnabled() {
        return PlayerConfig.isContextBarEnabled();
    }

    private static boolean handles(MessageObject messageObject) {
        return enabled() && messageObject != null && messageObject.isMusic();
    }

    public static BottomSheet create(Context context, Theme.ResourcesProvider resourcesProvider) {
        if (handles(MediaController.getInstance().getPlayingMessageObject())) {
            if (PlayerSheet.instance != null) {
                PlayerSheet.instance.dismissImmediately();
            }
            return new PlayerSheet(context, resourcesProvider);
        }
        return new AudioPlayerAlert(context, resourcesProvider);
    }

    public static void open(BaseFragment fragment, PlayerTransitionSource source) {
        if (fragment == null) {
            try {
                org.telegram.ui.LaunchActivity activity = org.telegram.ui.LaunchActivity.instance;
                if (activity != null && activity.getActionBarLayout() != null) {
                    fragment = activity.getActionBarLayout().getLastFragment();
                }
            } catch (Throwable ignored) {
            }
        }
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        MessageObject messageObject = MediaController.getInstance().getPlayingMessageObject();
        if (messageObject == null) {
            return;
        }
        BottomSheet sheet = create(fragment.getParentActivity(), fragment.getResourceProvider());
        if (sheet instanceof PlayerSheet && !PlayerConfig.isSlideAnimation() && source != null && source.canTransition()) {
            ((PlayerSheet) sheet).setTransitionSource(source);
        }
        fragment.showDialog(sheet);
    }

    public static boolean hidesContextPlayer(BaseFragment fragment, MessageObject messageObject) {
        if (!miniEnabled() || fragment == null || messageObject == null || !messageObject.isMusic()) {
            return false;
        }
        Bundle args = fragment.getArguments();
        return args != null && args.getBoolean("hasMainTabs", false);
    }
}
