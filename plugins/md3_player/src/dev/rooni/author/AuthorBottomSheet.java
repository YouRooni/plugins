package dev.rooni.author;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableStringBuilder;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.core.content.ContextCompat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.StickerImageView;
import org.telegram.ui.LaunchActivity;

public class AuthorBottomSheet extends BottomSheet {

    public static final long AUTHOR_CHANNEL_CHAT_ID = 2813564336L;
    public static final long AUTHOR_CHANNEL_DIALOG_ID = -1002813564336L;

    public static boolean isSubscribedToAuthor() {
        try {
            int account = UserConfig.selectedAccount;
            MessagesController mc = MessagesController.getInstance(account);
            if (mc == null) {
                return false;
            }

            TLRPC.Dialog dialog = mc.dialogs_dict.get(AUTHOR_CHANNEL_DIALOG_ID);
            if (dialog != null) {
                return true;
            }

            TLRPC.Chat chat = mc.getChat(AUTHOR_CHANNEL_CHAT_ID);
            if (chat != null && !ChatObject.isNotInChat(chat)) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean showFromCurrentActivity() {
        try {
            BaseFragment fragment = LaunchActivity.getLastFragment();
            if (fragment != null && fragment.getParentActivity() != null) {
                show(fragment);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static void show(final BaseFragment fragment) {
        if (fragment == null || fragment.getParentActivity() == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                try {
                    AuthorBottomSheet sheet = new AuthorBottomSheet(fragment);
                    fragment.showDialog(sheet);
                } catch (Throwable ignored) {}
            }
        });
    }

    public AuthorBottomSheet(final BaseFragment fragment) {
        super(fragment.getParentActivity(), false, fragment.getResourceProvider());
        final Context context = getContext();
        fixNavigationBar();

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(0, 0, 0, AndroidUtilities.dp(16));

        StickerImageView stickerView = new StickerImageView(context, UserConfig.selectedAccount);
        stickerView.setStickerPackName("RnLainy");
        stickerView.setStickerNum(7);
        contentLayout.addView(stickerView, LayoutHelper.createLinear(110, 110, Gravity.CENTER_HORIZONTAL, 0, 20, 0, 0));

        TextView titleView = new TextView(context);
        titleView.setText("Плагины от Rooni");
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
        titleView.setTextSize(1, 20.0f);
        titleView.setGravity(Gravity.CENTER_HORIZONTAL);
        contentLayout.addView(titleView, LayoutHelper.createLinear(-2, -2, Gravity.CENTER_HORIZONTAL, 22, 12, 22, 0));

        TextView subtitleView = new TextView(context);
        subtitleView.setText("Спасибо за интерес к моим плагинам! В них вложено много времени и внимания к деталям.");
        subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        subtitleView.setTextSize(1, 14.0f);
        subtitleView.setGravity(Gravity.CENTER_HORIZONTAL);
        subtitleView.setLineSpacing(AndroidUtilities.dp(2), 1.0f);
        contentLayout.addView(subtitleView, LayoutHelper.createLinear(-2, -2, Gravity.CENTER_HORIZONTAL, 26, 8, 26, 0));

        SpannableStringBuilder channelSpan = AndroidUtilities.replaceSingleTag(
                "Прочие плагины, обновления, модули и разное другое выходит в **@RnPlugins**",
                Theme.key_chat_messageLinkIn,
                0,
                new Runnable() {
                    @Override
                    public void run() {
                        dismiss();
                        openUsername("RnPlugins", fragment);
                    }
                }
        );
        FeatureCell channelCell = new FeatureCell(
                context,
                R.drawable.msg_channel,
                "Канал автора",
                channelSpan
        );
        contentLayout.addView(channelCell, LayoutHelper.createLinear(-1, -2, 0.0f, 0, 0, 18, 0, 0));

        SpannableStringBuilder authorSpan = AndroidUtilities.replaceSingleTag(
                "Связь, проекты и реквизиты автора - **@YouRooni**",
                Theme.key_chat_messageLinkIn,
                0,
                new Runnable() {
                    @Override
                    public void run() {
                        dismiss();
                        openUsername("YouRooni", fragment);
                    }
                }
        );
        FeatureCell authorCell = new FeatureCell(
                context,
                R.drawable.msg_openprofile,
                "Автор",
                authorSpan
        );
        contentLayout.addView(authorCell, LayoutHelper.createLinear(-1, -2, 0.0f, 0, 0, 14, 0, 0));

        LinearLayout buttonsRow = new LinearLayout(context);
        buttonsRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonsRow.setGravity(Gravity.CENTER_VERTICAL);

        int primaryColor = getThemedColor(Theme.key_featuredStickers_addButton);
        int primaryTextColor = getThemedColor(Theme.key_featuredStickers_buttonText);

        int secondaryBgColor = getThemedColor(Theme.key_windowBackgroundGray);
        if (secondaryBgColor == 0) {
            secondaryBgColor = 0xff252525;
        }
        int secondaryTextColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);

        int rBig = AndroidUtilities.dp(24);
        int rSmall = AndroidUtilities.dp(6);

        TextView btnGo = new TextView(context);
        btnGo.setText("Перейти");
        btnGo.setTextSize(1, 15.0f);
        btnGo.setTypeface(AndroidUtilities.bold());
        btnGo.setTextColor(primaryTextColor);
        btnGo.setGravity(Gravity.CENTER);

        Drawable leftSelector = Theme.createSimpleSelectorRoundRectDrawable(
                rBig, rSmall, rSmall, rBig,
                primaryColor,
                Theme.blendOver(primaryColor, 0x33ffffff),
                primaryColor
        );
        btnGo.setBackground(leftSelector);

        btnGo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
                openUsername("RnPlugins", fragment);
            }
        });

        TextView btnClose = new TextView(context);
        btnClose.setText("Закрыть");
        btnClose.setTextSize(1, 15.0f);
        btnClose.setTypeface(AndroidUtilities.bold());
        btnClose.setTextColor(secondaryTextColor);
        btnClose.setGravity(Gravity.CENTER);

        Drawable rightSelector = Theme.createSimpleSelectorRoundRectDrawable(
                rSmall, rBig, rBig, rSmall,
                secondaryBgColor,
                Theme.blendOver(secondaryBgColor, 0x33ffffff),
                secondaryBgColor
        );
        btnClose.setBackground(rightSelector);

        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dismiss();
            }
        });

        LinearLayout.LayoutParams lpLeft = new LinearLayout.LayoutParams(0, AndroidUtilities.dp(48), 1.0f);
        lpLeft.rightMargin = AndroidUtilities.dp(4);
        buttonsRow.addView(btnGo, lpLeft);

        LinearLayout.LayoutParams lpRight = new LinearLayout.LayoutParams(0, AndroidUtilities.dp(48), 1.0f);
        buttonsRow.addView(btnClose, lpRight);

        contentLayout.addView(buttonsRow, LayoutHelper.createLinear(-1, 48, 0, 18, 22, 18, 6));

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(contentLayout);
        setCustomView(scrollView);
    }

    private static void openUsername(String username, BaseFragment fragment) {
        try {
            if (fragment != null) {
                MessagesController.getInstance(UserConfig.selectedAccount).openByUserName(username, fragment, 1);
            }
        } catch (Throwable ignored) {}
    }

    public class FeatureCell extends FrameLayout {
        public FeatureCell(Context context, int iconRes, CharSequence title, CharSequence subtitle) {
            super(context);

            boolean isRtl = LocaleController.isRTL;
            ImageView iconView = new ImageView(context);
            Drawable iconDrawable = ContextCompat.getDrawable(context, iconRes);
            if (iconDrawable != null) {
                iconDrawable = iconDrawable.mutate();
                int iconColor = getThemedColor(Theme.key_windowBackgroundWhiteBlackText);
                iconDrawable.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.MULTIPLY));
                iconView.setImageDrawable(iconDrawable);
            }
            addView(iconView, LayoutHelper.createFrame(24, 24.0f, isRtl ? Gravity.RIGHT : Gravity.LEFT, isRtl ? 0.0f : 24.0f, 6.0f, isRtl ? 24.0f : 0.0f, 0.0f));

            TextView titleText = new TextView(context);
            titleText.setText(title);
            titleText.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            titleText.setTextSize(1, 14.5f);
            titleText.setTypeface(AndroidUtilities.bold());
            addView(titleText, LayoutHelper.createFrame(-2, -2.0f, isRtl ? Gravity.RIGHT : Gravity.LEFT, isRtl ? 24.0f : 62.0f, 0.0f, isRtl ? 62.0f : 24.0f, 0.0f));

            LinkSpanDrawable.LinksTextView subtitleText = new LinkSpanDrawable.LinksTextView(context);
            subtitleText.setText(subtitle);
            subtitleText.setTextSize(1, 13.5f);
            subtitleText.setTextColor(getThemedColor(Theme.key_dialogTextGray3));
            subtitleText.setLinkTextColor(getThemedColor(Theme.key_chat_messageLinkIn));
            subtitleText.setLineSpacing(AndroidUtilities.dp(2.0f), 1.0f);
            addView(subtitleText, LayoutHelper.createFrame(-2, -2.0f, isRtl ? Gravity.RIGHT : Gravity.LEFT, isRtl ? 24.0f : 62.0f, 20.0f, isRtl ? 62.0f : 24.0f, 0.0f));
        }
    }
}
