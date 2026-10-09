package dev.rooni.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

public class PlayerBarView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, PlayerTransitionSource {

    public static final int HEIGHT_DP = 52;

    private final Theme.ResourcesProvider resourcesProvider;
    private final CoverImage cover;
    private final TextView titleView;
    private final TextView artistView;
    private final RingPlayButton playButton;
    private final ImageView nextButton;
    private final ImageView closeButton;
    private final PlayerIcon nextIcon = PlayerIcon.fill(PlayerIcon.NEXT, 22);
    private final PlayerIcon closeIcon = PlayerIcon.stroke(PlayerIcon.CLOSE, 20);
    private MessageObject current;

    public PlayerBarView(Context context, Theme.ResourcesProvider resourcesProvider, Runnable onOpen, Runnable onClose) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        PlayerColors theme = PlayerColors.fromSeed(PlayerColors.themeSeed(resourcesProvider), PlayerColors.isDark(resourcesProvider));

        int bgColor = Theme.getColor(Theme.key_inappPlayerBackground, resourcesProvider);
        setBackgroundColor(bgColor);
        if (Build.VERSION.SDK_INT >= 23) {
            setForeground(Theme.getSelectorDrawable(false));
        }
        setOnClickListener(v -> onOpen.run());
        setContentDescription(PlayerStrings.get("open"));

        cover = new CoverImage(context, 18);
        cover.setRadius(dp(10));
        addView(cover, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(context);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView, LayoutHelper.createLinear(-1, -2));
        artistView = new TextView(context);
        artistView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        artistView.setSingleLine(true);
        artistView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(artistView, LayoutHelper.createLinear(-1, -2, 0, 1, 0, 0));
        addView(texts, LayoutHelper.createFrame(-1, -2, Gravity.LEFT | Gravity.CENTER_VERTICAL, 60, 0, 6 + 40 * 3 + 4, 0));

        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        playButton = new RingPlayButton(context, 15, 2.5f, 16, 1.1f, 10);
        playButton.setOnClickListener(v -> {
            MediaController mc = MediaController.getInstance();
            MessageObject mo = mc.getPlayingMessageObject();
            if (mo == null || mc.isDownloadingCurrentMessage()) {
                return;
            }
            if (mc.isMessagePaused()) {
                mc.playMessage(mo);
            } else {
                mc.pauseMessage(mo);
            }
        });
        buttons.addView(playButton, LayoutHelper.createLinear(40, 40));
        nextButton = new ImageView(context);
        nextButton.setScaleType(ImageView.ScaleType.CENTER);
        nextButton.setImageDrawable(nextIcon);
        nextButton.setContentDescription(getString(R.string.Next));
        nextButton.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        buttons.addView(nextButton, LayoutHelper.createLinear(40, 40));
        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageDrawable(closeIcon);
        closeButton.setContentDescription(getString(R.string.AccDescrClosePlayer));
        closeButton.setOnClickListener(v -> onClose.run());
        buttons.addView(closeButton, LayoutHelper.createLinear(40, 40));
        addView(buttons, LayoutHelper.createFrame(-2, 40, Gravity.RIGHT | Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

        int text = Theme.getColor(Theme.key_inappPlayerPerformer, resourcesProvider);
        int secondary = Theme.getColor(Theme.key_inappPlayerTitle, resourcesProvider);
        int icons = Theme.getColor(Theme.key_inappPlayerClose, resourcesProvider);
        titleView.setTextColor(text);
        artistView.setTextColor(ColorUtils.setAlphaComponent(secondary, 0xbf));
        nextIcon.setColor(icons);
        closeIcon.setColor(icons);
        int ripple = icons & 0x19ffffff;
        nextButton.setBackground(Theme.createSelectorDrawable(ripple, 1));
        closeButton.setBackground(Theme.createSelectorDrawable(ripple, 1));
        playButton.setBackground(Theme.createSelectorDrawable(ripple, 1));
        int accent = Theme.getColor(Theme.key_inappPlayerPlayPause, resourcesProvider);
        playButton.setColors(accent, ColorUtils.setAlphaComponent(accent, 51), accent);
        cover.setColors(theme.primaryContainer, theme.onPrimaryContainer);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.addObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.addObserver(this, NotificationCenter.fileLoaded);
            nc.addObserver(this, NotificationCenter.musicDidLoad);
        }
        update();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.removeObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.removeObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.removeObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.removeObserver(this, NotificationCenter.fileLoaded);
            nc.removeObserver(this, NotificationCenter.musicDidLoad);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        try {
            if (id == NotificationCenter.messagePlayingProgressDidChanged) {
                playButton.progressChanged();
            } else if (id == NotificationCenter.musicDidLoad) {
                MessageObject mo = current;
                if (mo != null && PlayerArt.isPlaying(mo)) {
                    cover.setMessage(mo);
                }
            } else if (id == NotificationCenter.fileLoaded) {
                MessageObject mo = current;
                if (mo != null && mo.getDocument() != null && args != null && args.length > 0 && TextUtils.equals(String.valueOf(args[0]), FileLoader.getAttachFileName(mo.getDocument()))) {
                    cover.setMessage(mo);
                }
            } else {
                update();
            }
        } catch (Throwable ignored) {
        }
    }

    public void update() {
        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
        if (mo == null || !mo.isMusic()) {
            return;
        }
        current = mo;
        titleView.setText(mo.getMusicTitle());
        artistView.setText(mo.getMusicAuthor());
        cover.setMessage(mo);
        playButton.setMessage(mo);
        boolean paused = MediaController.getInstance().isMessagePaused();
        playButton.setPlaying(!paused, isAttachedToWindow());
        playButton.setContentDescription(getString(paused ? R.string.AccActionPlay : R.string.AccActionPause));
    }

    @Override
    public boolean canTransition() {
        return isAttachedToWindow() && getVisibility() == VISIBLE && getWidth() > 0 && cover != null && cover.getWidth() > 0;
    }

    @Override
    public void getCardRect(RectF out) {
        int[] loc = new int[2];
        getLocationOnScreen(loc);
        out.set(loc[0], loc[1], loc[0] + getWidth(), loc[1] + getHeight());
    }

    @Override
    public void getCoverRect(RectF out) {
        int[] loc = new int[2];
        cover.getLocationOnScreen(loc);
        out.set(loc[0], loc[1], loc[0] + cover.getWidth(), loc[1] + cover.getHeight());
    }

    @Override
    public float getCardRadius() {
        return 0f;
    }

    @Override
    public float getCoverRadius() {
        return dp(10);
    }

    @Override
    public int getCardColor() {
        int bgColor = Theme.getColor(Theme.key_inappPlayerBackground, resourcesProvider);
        return bgColor != 0 ? bgColor : 0xff202020;
    }

    @Override
    public Bitmap getCoverBitmap() {
        return cover.getImageReceiver().getBitmap();
    }

    @Override
    public Bitmap captureCard() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        int visibility = cover.getVisibility();
        try {
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            cover.setVisibility(INVISIBLE);
            draw(canvas);
            return bitmap;
        } catch (Throwable e) {
            return null;
        } finally {
            cover.setVisibility(visibility);
        }
    }

    @Override
    public void setTransitionHidden(boolean hidden) {
        setAlpha(hidden ? 0f : 1f);
    }
}
