package dev.rooni.player;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

public class PlayerMiniView extends FrameLayout implements NotificationCenter.NotificationCenterDelegate, PlayerTransitionSource {

    public static final int HEIGHT_DP = 64;

    private BaseFragment fragment;
    private final Theme.ResourcesProvider resourcesProvider;
    private boolean floatingAllowed = true;
    private boolean dark;
    private int seed;
    private final LinearLayout card;
    private final GradientDrawable cardBg = new GradientDrawable();
    private final CoverImage cover;
    private final TextView titleView;
    private final TextView artistView;
    private final RingPlayButton playButton;
    private final ImageView nextButton;
    private final ImageView closeButton;
    private final PlayerIcon nextIcon = PlayerIcon.fill(PlayerIcon.NEXT, 24);
    private final PlayerIcon closeIcon = PlayerIcon.stroke(PlayerIcon.CLOSE, 22);
    private PlayerColors colors;
    private ValueAnimator showAnimator;
    private ValueAnimator colorAnimator;
    private int currentColorPrimaryContainer;
    private int currentColorOnPrimaryContainer;
    private int currentColorSecondaryContainer;
    private int currentColorOnSecondaryContainer;
    private MessageObject current;
    private boolean shown;
    private float showProgress;
    private float hostFactor = 1f;
    private Runnable offsetListener;

    private float currentCardY;
    private float downX;
    private float downY;
    private float startDragCardY;
    private boolean isDragging;
    private int touchSlop;
    private VelocityTracker velocityTracker;
    private ValueAnimator snapAnimator;
    private boolean transitionHidden;

    public PlayerMiniView(Context context, BaseFragment fragment, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.fragment = fragment;
        this.resourcesProvider = resourcesProvider;
        dark = PlayerColors.isDark(resourcesProvider);
        seed = PlayerColors.fallbackSeed(resourcesProvider);
        colors = PlayerColors.fromSeed(seed, dark);
        setClipChildren(false);
        setClipToPadding(false);

        card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(10), 0, dp(6), 0);
        cardBg.setCornerRadius(dp(20));
        card.setBackground(cardBg);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(20));
            }
        });
        card.setClipToOutline(true);
        card.setElevation(dp(6));
        card.setOnClickListener(v -> Md3Player.open(fragment, this));
        card.setContentDescription(PlayerStrings.get("open"));
        addView(card, LayoutHelper.createFrame(-1, HEIGHT_DP, Gravity.TOP, 12, 0, 12, 0));
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        cover = new CoverImage(context, 22);
        cover.setRadius(dp(12));
        cover.setListener(PlayerColors.themeSeed(resourcesProvider), (mo, s) -> {
            if (PlayerConfig.isColorFromCover() && mo == current) {
                this.seed = s;
                animateColors(PlayerColors.fromSeed(s, dark));
            }
        });
        card.addView(cover, LayoutHelper.createLinear(44, 44));

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
        artistView.setAlpha(0.8f);
        texts.addView(artistView, LayoutHelper.createLinear(-1, -2, 0, 2, 0, 0));
        card.addView(texts, LayoutHelper.createLinear(0, -2, 1f, 12, 0, 4, 0));

        playButton = new RingPlayButton(context, 20, 3, 22, 1.3f, 12);
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
        card.addView(playButton, LayoutHelper.createLinear(48, 48));

        nextButton = new ImageView(context);
        nextButton.setScaleType(ImageView.ScaleType.CENTER);
        nextButton.setImageDrawable(nextIcon);
        nextButton.setContentDescription(getString(R.string.Next));
        nextButton.setOnClickListener(v -> MediaController.getInstance().playNextMessage());
        card.addView(nextButton, LayoutHelper.createLinear(44, 48));

        closeButton = new ImageView(context);
        closeButton.setScaleType(ImageView.ScaleType.CENTER);
        closeButton.setImageDrawable(closeIcon);
        closeButton.setContentDescription(getString(R.string.AccDescrClosePlayer));
        closeButton.setOnClickListener(v -> MediaController.getInstance().cleanupPlayer(true, true));
        card.addView(closeButton, LayoutHelper.createLinear(44, 48));

        applyColors(colors);
        setVisibility(GONE);
        currentCardY = PlayerConfig.isMiniPlayerTop() ? calculateTopBound() : calculateBottomBound();
        card.setTranslationY(currentCardY);
    }

    public boolean canTransition() {
        return isAttachedToWindow() && getVisibility() == VISIBLE && shown && showProgress >= 0.9f && hostFactor >= 0.9f && card.getWidth() > 0 && !isDragging;
    }

    public void getCardRect(RectF out) {
        locate(card, out);
    }

    public void getCoverRect(RectF out) {
        locate(cover, out);
    }

    private static void locate(View view, RectF out) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        out.set(loc[0], loc[1], loc[0] + view.getWidth(), loc[1] + view.getHeight());
    }

    public float getCardRadius() {
        return dp(20);
    }

    public float getCoverRadius() {
        return dp(12);
    }

    public int getCardColor() {
        return currentColorPrimaryContainer != 0 ? currentColorPrimaryContainer : getCardBg(colors);
    }

    public Bitmap getCoverBitmap() {
        return cover.getImageReceiver().getBitmap();
    }

    public Bitmap captureCard() {
        int w = card.getWidth();
        int h = card.getHeight();
        if (w <= 0 || h <= 0) {
            return null;
        }
        int visibility = cover.getVisibility();
        try {
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            cover.setVisibility(INVISIBLE);
            card.setPressed(false);
            card.jumpDrawablesToCurrentState();
            card.draw(canvas);
            return bitmap;
        } catch (Throwable e) {
            return null;
        } finally {
            cover.setVisibility(visibility);
        }
    }

    public void setTransitionHidden(boolean hidden) {
        transitionHidden = hidden;
        card.setAlpha(hidden ? 0f : (showProgress * hostFactor));
        if (!hidden) {
            if (cover != null && cover.getVisibility() != VISIBLE) {
                cover.setVisibility(VISIBLE);
            }
            card.setVisibility(VISIBLE);
            setVisibility(showProgress > 0f ? VISIBLE : GONE);
            card.invalidate();
            invalidate();
        }
    }

    public void setCurrentFragment(BaseFragment fragment) {
        if (this.fragment == fragment) {
            return;
        }
        this.fragment = fragment;
        if (!transitionHidden && !isDragging && (snapAnimator == null || !snapAnimator.isRunning())) {
            boolean isTop = PlayerConfig.isMiniPlayerTop();
            currentCardY = isTop ? calculateTopBound() : calculateBottomBound();
            applyTransform();
        }
    }

    public void setFloatingAllowed(boolean allowed) {
        if (floatingAllowed != allowed) {
            floatingAllowed = allowed;
            update(false);
        }
    }

    public void setOffsetListener(Runnable listener) {
        offsetListener = listener;
    }

    public float getVisibleOffset() {
        if (PlayerConfig.isMiniPlayerTop()) {
            return 0f;
        }
        return (dp(HEIGHT_DP) + dp(8)) * showProgress * hostFactor;
    }

    public int getTargetOffset() {
        if (PlayerConfig.isMiniPlayerTop()) {
            return 0;
        }
        return shown && hostFactor > 0.5f ? dp(HEIGHT_DP) + dp(8) : 0;
    }

    public void setHostPosition(float translation, float factor) {
        float old = hostFactor;
        hostFactor = factor;
        applyTransform();
        if (old != factor) {
            notifyOffset();
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!transitionHidden && !isDragging && (snapAnimator == null || !snapAnimator.isRunning())) {
            boolean isTop = PlayerConfig.isMiniPlayerTop();
            currentCardY = isTop ? calculateTopBound() : calculateBottomBound();
            applyTransform();
        }
    }

    private int calculateTopBound() {
        int top = 0;
        if (fragment != null && fragment.getActionBar() != null && fragment.getActionBar().getVisibility() == VISIBLE) {
            int h = fragment.getActionBar().getHeight();
            if (h <= 0) h = fragment.getActionBar().getMeasuredHeight();
            if (h <= 0) h = AndroidUtilities.dp(56);
            top = fragment.getActionBar().getTop() + h;
        }
        if (top == 0) {
            top = AndroidUtilities.statusBarHeight + AndroidUtilities.dp(56);
        }
        if (fragment != null) {
            try {
                Field f = fragment.getClass().getDeclaredField("filterTabsView");
                f.setAccessible(true);
                View tabs = (View) f.get(fragment);
                if (tabs != null && tabs.getVisibility() == VISIBLE) {
                    int th = tabs.getHeight() > 0 ? tabs.getHeight() : tabs.getMeasuredHeight();
                    top = Math.max(top, tabs.getTop() + th);
                }
            } catch (Throwable ignored) {
            }
        }
        View parent = (View) getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup p = (ViewGroup) parent;
            for (int i = 0; i < p.getChildCount(); i++) {
                View child = p.getChildAt(i);
                if (child != this && child.getVisibility() == VISIBLE) {
                    String name = child.getClass().getSimpleName();
                    if (name.contains("TabsView") || name.contains("TopPanel") || name.contains("ActionBar")) {
                        int ph = getHeight() > 0 ? getHeight() : 1000;
                        if (child.getY() < ph / 3f) {
                            int b = (int) (child.getY() + (child.getHeight() > 0 ? child.getHeight() : child.getMeasuredHeight()));
                            if (b > top) {
                                top = b;
                            }
                        }
                    }
                }
            }
        }
        return top + dp(8);
    }

    private int calculateBottomBound() {
        int h = getHeight();
        if (h <= 0) {
            h = AndroidUtilities.displaySize.y;
        }
        int inset = calculateBottomInset();
        int bound = h - dp(HEIGHT_DP) - inset - dp(12);
        int topBound = calculateTopBound();
        if (bound < topBound + dp(HEIGHT_DP)) {
            bound = topBound + dp(HEIGHT_DP) + dp(16);
        }
        return bound;
    }

    private int calculateBottomInset() {
        int bottom = 0;
        View parent = (View) getParent();
        if (parent instanceof ViewGroup) {
            ViewGroup p = (ViewGroup) parent;
            for (int i = 0; i < p.getChildCount(); i++) {
                View child = p.getChildAt(i);
                if (child != this && child.getVisibility() == VISIBLE) {
                    String name = child.getClass().getSimpleName();
                    if (name.contains("TabsView") || name.contains("BottomNav") || name.contains("Navigation")) {
                        int h = child.getHeight() > 0 ? child.getHeight() : child.getMeasuredHeight();
                        if (h > bottom) {
                            bottom = h;
                        }
                    }
                }
            }
        }
        if (bottom == 0 && fragment != null) {
            try {
                Field f = fragment.getClass().getDeclaredField("tabsViewWrapper");
                f.setAccessible(true);
                View v = (View) f.get(fragment);
                if (v != null && v.getVisibility() == VISIBLE) {
                    bottom = v.getHeight() > 0 ? v.getHeight() : v.getMeasuredHeight();
                }
            } catch (Throwable ignored) {
            }
            if (bottom == 0) {
                try {
                    Field f = fragment.getClass().getDeclaredField("tabsView");
                    f.setAccessible(true);
                    View v = (View) f.get(fragment);
                    if (v != null && v.getVisibility() == VISIBLE) {
                        bottom = v.getHeight() > 0 ? v.getHeight() : v.getMeasuredHeight();
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        if (bottom == 0) {
            bottom = AndroidUtilities.navigationBarHeight;
        }
        return bottom;
    }

    private void applyTransform() {
        float p = showProgress * hostFactor;
        boolean isTop = PlayerConfig.isMiniPlayerTop();
        float hideOffset = (isTop ? -dp(32) : dp(32)) * (1f - p);
        card.setTranslationY(currentCardY + hideOffset);
        card.setAlpha(transitionHidden ? 0f : p);
        setVisibility(p > 0f ? VISIBLE : GONE);
    }

    private boolean isTouchInsideCard(MotionEvent ev) {
        float x = ev.getX();
        float y = ev.getY();
        float cardLeft = card.getLeft();
        float cardRight = card.getRight();
        float cardTop = card.getTop() + card.getTranslationY();
        float cardBottom = card.getBottom() + card.getTranslationY();
        return x >= cardLeft && x <= cardRight && y >= cardTop && y <= cardBottom;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!shown || showProgress < 0.05f || !floatingAllowed) {
            return false;
        }
        if (isDragging) {
            return super.dispatchTouchEvent(ev);
        }
        if (isTouchInsideCard(ev)) {
            return super.dispatchTouchEvent(ev);
        }
        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isTouchInsideCard(ev)) {
            return false;
        }
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getRawX();
                downY = ev.getRawY();
                startDragCardY = card.getTranslationY();
                isDragging = false;
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                } else {
                    velocityTracker.clear();
                }
                velocityTracker.addMovement(ev);
                break;
            case MotionEvent.ACTION_MOVE:
                float dx = Math.abs(ev.getRawX() - downX);
                float dy = Math.abs(ev.getRawY() - downY);
                if (dy > touchSlop && dy > dx * 1.2f) {
                    isDragging = true;
                    card.setPressed(false);
                    card.setElevation(dp(10));
                    ViewParent parent = getParent();
                    if (parent != null) {
                        parent.requestDisallowInterceptTouchEvent(true);
                    }
                    if (velocityTracker != null) {
                        velocityTracker.addMovement(ev);
                    }
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                break;
        }
        return isDragging;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (velocityTracker != null) {
            velocityTracker.addMovement(ev);
        }
        int action = ev.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (isTouchInsideCard(ev)) {
                    downX = ev.getRawX();
                    downY = ev.getRawY();
                    startDragCardY = card.getTranslationY();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_MOVE:
                if (!isDragging) {
                    float dx = Math.abs(ev.getRawX() - downX);
                    float dy = Math.abs(ev.getRawY() - downY);
                    if (dy > touchSlop && dy > dx * 1.2f) {
                        isDragging = true;
                        card.setPressed(false);
                        card.setElevation(dp(10));
                        ViewParent parent = getParent();
                        if (parent != null) {
                            parent.requestDisallowInterceptTouchEvent(true);
                        }
                    }
                }
                if (isDragging) {
                    float deltaY = ev.getRawY() - downY;
                    float rawTargetY = startDragCardY + deltaY;
                    float topBound = calculateTopBound();
                    float bottomBound = calculateBottomBound();

                    float actualY;
                    if (rawTargetY < topBound) {
                        float over = topBound - rawTargetY;
                        actualY = topBound - over * 0.22f;
                    } else if (rawTargetY > bottomBound) {
                        float over = rawTargetY - bottomBound;
                        actualY = bottomBound + over * 0.22f;
                    } else {
                        actualY = rawTargetY;
                    }

                    currentCardY = actualY;
                    card.setTranslationY(actualY);
                    card.setScaleX(0.965f);
                    card.setScaleY(0.965f);
                    return true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (isDragging) {
                    isDragging = false;
                    float vY = 0;
                    if (velocityTracker != null) {
                        velocityTracker.computeCurrentVelocity(1000);
                        vY = velocityTracker.getYVelocity();
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }

                    float topBound = calculateTopBound();
                    float bottomBound = calculateBottomBound();
                    float mid = (topBound + bottomBound) / 2f;

                    boolean targetTop;
                    if (vY < -900) {
                        targetTop = true;
                    } else if (vY > 900) {
                        targetTop = false;
                    } else {
                        targetTop = card.getTranslationY() < mid;
                    }

                    snapTo(targetTop, vY);
                    return true;
                } else {
                    if (velocityTracker != null) {
                        velocityTracker.recycle();
                        velocityTracker = null;
                    }
                }
                break;
        }
        return isTouchInsideCard(ev) || super.onTouchEvent(ev);
    }

    private void snapTo(boolean toTop, float velocityY) {
        PlayerConfig.setMiniPlayerTop(toTop);
        float startY = card.getTranslationY();
        float endY = toTop ? calculateTopBound() : calculateBottomBound();

        if (snapAnimator != null) {
            snapAnimator.cancel();
        }

        float distance = Math.abs(endY - startY);
        long duration = Math.min(480, Math.max(280, (long) (distance / 2.2f)));

        ValueAnimator anim = ValueAnimator.ofFloat(0f, 1f);
        anim.setDuration(duration);
        anim.setInterpolator(new OvershootInterpolator(1.2f));
        anim.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            currentCardY = startY + (endY - startY) * f;
            card.setTranslationY(currentCardY);

            float scale;
            if (f < 0.6f) {
                scale = 0.965f + 0.045f * (f / 0.6f);
            } else {
                scale = 1.01f - 0.01f * ((f - 0.6f) / 0.4f);
            }
            card.setScaleX(scale);
            card.setScaleY(scale);
        });
        anim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                currentCardY = endY;
                card.setTranslationY(endY);
                card.setScaleX(1f);
                card.setScaleY(1f);
                card.setElevation(dp(6));
                snapAnimator = null;
                notifyOffset();
            }
        });
        snapAnimator = anim;
        anim.start();
        notifyOffset();
    }

    private void notifyOffset() {
        if (offsetListener != null) {
            offsetListener.run();
        }
        updateFabOffset();
    }

    private void updateFabOffset() {
        float offset = getVisibleOffset();
        if (fragment != null) {
            try {
                Field fTrans = fragment.getClass().getDeclaredField("additionalFloatingTranslation");
                fTrans.setAccessible(true);
                fTrans.setFloat(fragment, offset);
                Method m = fragment.getClass().getDeclaredMethod("updateFloatingButtonOffset");
                m.setAccessible(true);
                m.invoke(fragment);
                return;
            } catch (Throwable ignored) {
            }

            try {
                Field fDa = fragment.getClass().getDeclaredField("dialogsActivity");
                fDa.setAccessible(true);
                Object da = fDa.get(fragment);
                if (da != null) {
                    Field fTrans = da.getClass().getDeclaredField("additionalFloatingTranslation");
                    fTrans.setAccessible(true);
                    fTrans.setFloat(da, offset);
                    Method m = da.getClass().getDeclaredMethod("updateFloatingButtonOffset");
                    m.setAccessible(true);
                    m.invoke(da);
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        View p = (View) getParent();
        if (p instanceof ViewGroup) {
            View fab = findFabView((ViewGroup) p);
            if (fab != null) {
                fab.setTranslationY(-offset);
            }
        }
    }

    private View findFabView(ViewGroup root) {
        if (root == null) return null;
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child != this) {
                String name = child.getClass().getSimpleName();
                if (name.contains("FloatingButton") || name.contains("floatingButton")) {
                    return child;
                }
                if (child instanceof ViewGroup && !(name.contains("Recycler") || name.contains("List"))) {
                    View sub = findFabView((ViewGroup) child);
                    if (sub != null) return sub;
                }
            }
        }
        return null;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.addObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.addObserver(this, NotificationCenter.messagePlayingDidReset);
            nc.addObserver(this, NotificationCenter.messagePlayingPlayStateChanged);
            nc.addObserver(this, NotificationCenter.messagePlayingProgressDidChanged);
            nc.addObserver(this, NotificationCenter.fileLoaded);
            nc.addObserver(this, NotificationCenter.musicDidLoad);
        }
        if (!isDragging && (snapAnimator == null || !snapAnimator.isRunning())) {
            boolean isTop = PlayerConfig.isMiniPlayerTop();
            currentCardY = isTop ? calculateTopBound() : calculateBottomBound();
        }
        update(false);
        applyTransform();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            NotificationCenter nc = NotificationCenter.getInstance(a);
            nc.removeObserver(this, NotificationCenter.messagePlayingDidStart);
            nc.removeObserver(this, NotificationCenter.messagePlayingDidReset);
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
                update(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private void checkThemeColors() {
        boolean themeDark = PlayerColors.isDark(resourcesProvider);
        int themeSeed = PlayerColors.themeSeed(resourcesProvider);
        if (themeDark != dark || themeSeed != seed) {
            dark = themeDark;
            seed = themeSeed;
            if (colors == null) {
                applyColors(PlayerColors.fromSeed(seed, dark));
            } else {
                animateColors(PlayerColors.fromSeed(seed, dark));
            }
        }
    }

    public void update(boolean animated) {
        MessageObject mo = MediaController.getInstance().getPlayingMessageObject();
        boolean isMain = PlayerHook.isMainDialogs(fragment);
        boolean want = floatingAllowed && isMain && PlayerConfig.isMiniPlayerDialogs() && mo != null && mo.isMusic() && mo.getId() != 0;
        if (want) {
            bind(mo);
        } else {
            checkThemeColors();
        }
        playButton.setPlaying(want && !MediaController.getInstance().isMessagePaused(), animated);
        setShown(want, animated);
    }

    private void bind(MessageObject mo) {
        boolean same = current == mo;
        current = mo;
        if (!same) {
            titleView.setText(mo.getMusicTitle());
            artistView.setText(mo.getMusicAuthor());
            cover.setMessage(mo);
            playButton.setMessage(mo);
        }
        playButton.setContentDescription(getString(MediaController.getInstance().isMessagePaused() ? R.string.AccActionPlay : R.string.AccActionPause));

        dark = PlayerColors.isDark(resourcesProvider);
        if (PlayerConfig.isColorFromCover()) {
            Integer cached = PlayerArt.cachedSeed(mo);
            if (cached != null) {
                seed = cached;
                if (!same) {
                    animateColors(PlayerColors.fromSeed(seed, dark));
                }
            }
        } else {
            checkThemeColors();
        }
    }

    private void setShown(boolean value, boolean animated) {
        if (shown == value && (showAnimator != null || showProgress == (value ? 1f : 0f))) {
            if (!transitionHidden && value && card.getAlpha() < 0.1f) {
                applyTransform();
            }
            return;
        }
        shown = value;
        if (showAnimator != null) {
            showAnimator.cancel();
            showAnimator = null;
        }
        float target = value ? 1f : 0f;
        if (!animated || !isAttachedToWindow()) {
            showProgress = target;
            applyTransform();
            notifyOffset();
            return;
        }
        showAnimator = ValueAnimator.ofFloat(showProgress, target);
        showAnimator.addUpdateListener(a -> {
            showProgress = (float) a.getAnimatedValue();
            applyTransform();
            notifyOffset();
        });
        showAnimator.setDuration(value ? 450 : 300);
        showAnimator.setInterpolator(value ? new CubicBezierInterpolator(0.05, 0.7, 0.1, 1) : new CubicBezierInterpolator(0.3, 0, 0.8, 0.15));
        showAnimator.start();
    }

    private void animateColors(PlayerColors target) {
        if (target == null) {
            return;
        }
        if (colors == null) {
            applyColors(target);
            return;
        }
        if (colorAnimator != null) {
            colorAnimator.cancel();
            colorAnimator = null;
        }
        final int startBg = currentColorPrimaryContainer != 0 ? currentColorPrimaryContainer : getCardBg(colors);
        final int endBg = getCardBg(target);
        final int startFg = currentColorOnPrimaryContainer != 0 ? currentColorOnPrimaryContainer : getCardFg(colors);
        final int endFg = getCardFg(target);
        final int startSecBg = currentColorSecondaryContainer != 0 ? currentColorSecondaryContainer : target.secondaryContainer;
        final int endSecBg = target.secondaryContainer;
        final int startSecFg = currentColorOnSecondaryContainer != 0 ? currentColorOnSecondaryContainer : target.onSecondaryContainer;
        final int endSecFg = target.onSecondaryContainer;

        if (startBg == endBg && startFg == endFg) {
            applyColors(target);
            return;
        }

        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.setDuration(260);
        colorAnimator.setInterpolator(CubicBezierInterpolator.EASE_OUT);
        colorAnimator.addUpdateListener(anim -> {
            float f = (float) anim.getAnimatedValue();
            int bg = ColorUtils.blendARGB(startBg, endBg, f);
            int fg = ColorUtils.blendARGB(startFg, endFg, f);
            int secBg = ColorUtils.blendARGB(startSecBg, endSecBg, f);
            int secFg = ColorUtils.blendARGB(startSecFg, endSecFg, f);

            currentColorPrimaryContainer = bg;
            currentColorOnPrimaryContainer = fg;
            currentColorSecondaryContainer = secBg;
            currentColorOnSecondaryContainer = secFg;

            cardBg.setColor(bg);
            cover.setColors(secBg, secFg);
            titleView.setTextColor(fg);
            artistView.setTextColor(fg);
            nextIcon.setColor(fg);
            closeIcon.setColor(fg);
            playButton.setColors(fg, ColorUtils.setAlphaComponent(fg, 46), fg);
        });
        colorAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                applyColors(target);
                colorAnimator = null;
            }
        });
        colorAnimator.start();
    }

    private int getCardBg(PlayerColors c) {
        if (c == null) return dark ? 0xff1e1e1e : 0xfff5f5f5;
        if (dark) {
            if (ColorUtils.calculateLuminance(c.primaryContainer) > 0.45) {
                return c.surfaceHigh != 0 ? c.surfaceHigh : (c.surfaceContainer != 0 ? c.surfaceContainer : 0xff1e1e1e);
            }
            return c.primaryContainer;
        } else {
            if (ColorUtils.calculateLuminance(c.primaryContainer) < 0.4) {
                return c.surfaceContainer != 0 ? c.surfaceContainer : (c.surface != 0 ? c.surface : 0xfff5f5f5);
            }
            return c.primaryContainer;
        }
    }

    private int getCardFg(PlayerColors c) {
        if (c == null) return dark ? 0xffffffff : 0xff000000;
        int bg = getCardBg(c);
        if (ColorUtils.calculateLuminance(bg) < 0.5) {
            return ColorUtils.calculateLuminance(c.onPrimaryContainer) > 0.5 ? c.onPrimaryContainer : (c.onSurface != 0 ? c.onSurface : 0xffffffff);
        } else {
            return ColorUtils.calculateLuminance(c.onPrimaryContainer) < 0.5 ? c.onPrimaryContainer : (c.onSurface != 0 ? c.onSurface : 0xff000000);
        }
    }

    private void applyColors(PlayerColors c) {
        colors = c;
        int bg = getCardBg(c);
        int fg = getCardFg(c);
        currentColorPrimaryContainer = bg;
        currentColorOnPrimaryContainer = fg;
        currentColorSecondaryContainer = c.secondaryContainer;
        currentColorOnSecondaryContainer = c.onSecondaryContainer;

        cardBg.setColor(bg);
        if (Build.VERSION.SDK_INT >= 28) {
            card.setOutlineSpotShadowColor(c.shadow());
            card.setOutlineAmbientShadowColor(c.shadow());
        }
        cover.setColors(c.secondaryContainer, c.onSecondaryContainer);
        titleView.setTextColor(fg);
        artistView.setTextColor(fg);
        nextIcon.setColor(fg);
        closeIcon.setColor(fg);
        int ripple = ColorUtils.setAlphaComponent(fg, 0x1f);
        nextButton.setBackground(Theme.createSelectorDrawable(ripple, 1));
        closeButton.setBackground(Theme.createSelectorDrawable(ripple, 1));
        playButton.setBackground(Theme.createSelectorDrawable(ripple, 1));
        playButton.setColors(fg, ColorUtils.setAlphaComponent(fg, 46), fg);
        card.setForeground(Theme.createSelectorDrawable(ripple, 2));
    }
}
