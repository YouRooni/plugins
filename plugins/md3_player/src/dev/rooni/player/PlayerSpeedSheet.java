package dev.rooni.player;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.PathParser;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class PlayerSpeedSheet extends BottomSheet {

    private final PlayerColors colors;
    private final PlayerSheet parentSheet;
    private final TextView speedValueText;
    private final PitchChip pitchChip;
    private final SpeedDiscreteBar speedBar;

    public PlayerSpeedSheet(Context context, PlayerColors colors, Theme.ResourcesProvider resourcesProvider, PlayerSheet parentSheet) {
        super(context, false, resourcesProvider);
        this.colors = colors;
        this.parentSheet = parentSheet;
        occupyNavigationBar = true;
        drawNavigationBar = false;
        setApplyTopPadding(false);
        setApplyBottomPadding(false);

        FrameLayout root = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(colors.surfaceLow);
        bg.setCornerRadii(new float[]{dp(28), dp(28), dp(28), dp(28), 0, 0, 0, 0});
        root.setBackground(bg);
        containerView = root;

        View handle = new View(context);
        GradientDrawable handleBg = new GradientDrawable();
        handleBg.setCornerRadius(dp(2));
        handleBg.setColor(ColorUtils.setAlphaComponent(colors.onSurfaceVariant, 0x73));
        handle.setBackground(handleBg);
        root.addView(handle, LayoutHelper.createFrame(32, 4, Gravity.CENTER_HORIZONTAL | Gravity.TOP, 0, 14, 0, 0));

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(26), 0, dp(24));
        root.addView(content, LayoutHelper.createFrame(-1, -2, Gravity.TOP));

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        row1.setPadding(dp(24), dp(8), dp(24), dp(8));

        ImageView speedIcon = new ImageView(context);
        PlayerIcon icon = PlayerIcon.stroke(PlayerIcon.SPEED, 26);
        icon.setColor(colors.primary);
        speedIcon.setImageDrawable(icon);
        row1.addView(speedIcon, LayoutHelper.createLinear(28, 28, Gravity.CENTER_VERTICAL));

        float currentSpeed = MediaController.getInstance().getPlaybackSpeed(true);
        speedValueText = new TextView(context);
        speedValueText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        speedValueText.setTypeface(AndroidUtilities.bold());
        speedValueText.getPaint().setFakeBoldText(true);
        speedValueText.setTextColor(colors.onSurface);
        updateSpeedText(currentSpeed);
        row1.addView(speedValueText, LayoutHelper.createLinear(-2, -2, Gravity.CENTER_VERTICAL, 12, 0, 0, 0));

        View spacer = new View(context);
        row1.addView(spacer, LayoutHelper.createLinear(0, 0, 1f));

        pitchChip = new PitchChip(context, colors);
        row1.addView(pitchChip, LayoutHelper.createLinear(-2, 36, Gravity.CENTER_VERTICAL));
        content.addView(row1, LayoutHelper.createLinear(-1, -2));

        speedBar = new SpeedDiscreteBar(context, colors, currentSpeed, speed -> {
            updateSpeedText(speed);
            MediaController.getInstance().setPlaybackSpeed(true, speed);
            PlayerHook.applyPlaybackParameters();
            if (PlayerSpeedSheet.this.parentSheet != null) {
                PlayerSpeedSheet.this.parentSheet.updateSpeed();
            }
        });
        content.addView(speedBar, LayoutHelper.createLinear(-1, 56, 0, 10, 0, 0, 10));
    }

    private void updateSpeedText(float speed) {
        Locale loc = LocaleController.getInstance().getCurrentLocale();
        DecimalFormat format = new DecimalFormat("0.#", DecimalFormatSymbols.getInstance(loc != null ? loc : Locale.US));
        speedValueText.setText(format.format(speed) + "x");
    }

    private static class PitchChip extends View {

        private final PlayerColors colors;
        private boolean active;
        private float progress;
        private ValueAnimator animator;

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint checkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path checkPath;
        private final RectF bounds = new RectF();
        private final String text;

        public PitchChip(Context context, PlayerColors colors) {
            super(context);
            this.colors = colors;
            this.active = PlayerConfig.isPitchEnabled();
            this.progress = active ? 1f : 0f;
            this.text = PlayerStrings.get("pitch");

            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(dp(1));

            textPaint.setTextSize(dp(14));
            textPaint.setTypeface(AndroidUtilities.bold());

            checkPaint.setStyle(Paint.Style.STROKE);
            checkPaint.setStrokeWidth(dp(1.35f));
            checkPaint.setStrokeCap(Paint.Cap.ROUND);
            checkPaint.setStrokeJoin(Paint.Join.ROUND);

            Path parsed = null;
            try {
                parsed = PathParser.createPathFromPathData(PlayerIcon.CHECK);
            } catch (Throwable ignored) {
            }
            checkPath = parsed != null ? parsed : new Path();

            setOnClickListener(v -> {
                active = !active;
                PlayerConfig.setPitchEnabled(active);
                PlayerHook.applyPlaybackParameters();
                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                } catch (Throwable ignored) {
                }
                animateProgress(active ? 1f : 0f);
            });
        }

        private void animateProgress(float target) {
            if (animator != null) {
                animator.cancel();
            }
            animator = ValueAnimator.ofFloat(progress, target);
            animator.setDuration(220);
            animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
            animator.addUpdateListener(a -> {
                progress = (float) a.getAnimatedValue();
                invalidate();
            });
            animator.start();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            float textW = textPaint.measureText(text);
            int checkW = dp(18);
            int gap = dp(6);
            int basePadding = dp(14);
            int w = (int) (basePadding * 2 + textW + (checkW + gap) * progress);
            int h = dp(36);
            setMeasuredDimension(Math.max(w, dp(64)), h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            bounds.set(dp(1), dp(1), w - dp(1), h - dp(1));
            float r = bounds.height() / 2f;

            int normalBg = 0x00000000;
            int activeBg = colors.primaryContainer;
            int curBg = ColorUtils.blendARGB(normalBg, activeBg, progress);
            bgPaint.setColor(curBg);
            canvas.drawRoundRect(bounds, r, r, bgPaint);

            int normalStroke = colors.outlineVariant;
            int activeStroke = 0x00000000;
            int curStroke = ColorUtils.blendARGB(normalStroke, activeStroke, progress);
            if (Color.alpha(curStroke) > 0) {
                strokePaint.setColor(curStroke);
                canvas.drawRoundRect(bounds, r, r, strokePaint);
            }

            int normalText = colors.onSurfaceVariant;
            int activeText = colors.onPrimaryContainer;
            int curText = ColorUtils.blendARGB(normalText, activeText, progress);
            textPaint.setColor(curText);

            float textW = textPaint.measureText(text);
            float checkW = dp(18);
            float gap = dp(6);
            float totalContentW = textW + (checkW + gap) * progress;
            float startX = (w - totalContentW) / 2f;

            if (progress > 0.01f) {
                canvas.save();
                float checkLeft = startX;
                float checkTop = (h - checkW) / 2f;
                canvas.translate(checkLeft, checkTop);
                float s = checkW / 24f;
                canvas.scale(s, s);
                checkPaint.setColor(curText);
                checkPaint.setAlpha((int) (255 * progress));
                canvas.drawPath(checkPath, checkPaint);
                canvas.restore();
            }

            float textX = startX + (checkW + gap) * progress;
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float textY = (h - fm.bottom - fm.top) / 2f;
            canvas.drawText(text, textX, textY, textPaint);
        }
    }

    public static class SpeedDiscreteBar extends View {

        public interface OnSpeedChangeListener {
            void onSpeedChanged(float speed);
        }

        private static final int STEPS = 15;
        private static final float MIN_SPEED = 0.5f;
        private static final float STEP_SIZE = 0.1f;

        private final PlayerColors colors;
        private final OnSpeedChangeListener listener;
        private int stepIndex;
        private float thumbX = -1f;
        private boolean isDragging = false;
        private float thumbRadiusProgress = 0f;
        private ValueAnimator thumbRadiusAnim;
        private ValueAnimator snapAnim;

        private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint activeTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        public SpeedDiscreteBar(Context context, PlayerColors colors, float initialSpeed, OnSpeedChangeListener listener) {
            super(context);
            this.colors = colors;
            this.listener = listener;

            int initialIndex = Math.round((initialSpeed - MIN_SPEED) / STEP_SIZE);
            this.stepIndex = Math.max(0, Math.min(STEPS, initialIndex));

            trackPaint.setColor(ColorUtils.blendARGB(colors.surfaceHigh, colors.onSurface, 0.12f));
            activeTrackPaint.setColor(colors.primary);
            thumbPaint.setColor(colors.primary);
        }

        private float getXForIndex(int idx, float startX, float endX) {
            return startX + (endX - startX) * (idx / (float) STEPS);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            float cy = h / 2f;
            float startX = getPaddingLeft() + dp(24);
            float endX = w - getPaddingRight() - dp(24);

            if (thumbX < 0) {
                thumbX = getXForIndex(stepIndex, startX, endX);
            }

            float trackH = dp(16);
            float trackR = trackH / 2f;
            float gap = dp(5);

            float activeRight = Math.max(startX, thumbX - gap);
            float inactiveLeft = Math.min(endX, thumbX + gap);

            if (activeRight > startX + dp(2)) {
                canvas.drawRoundRect(startX, cy - trackR, activeRight, cy + trackR, trackR, trackR, activeTrackPaint);
            }

            if (inactiveLeft < endX - dp(2)) {
                canvas.drawRoundRect(inactiveLeft, cy - trackR, endX, cy + trackR, trackR, trackR, trackPaint);
            }

            for (int i = 0; i <= STEPS; i++) {
                float tx = getXForIndex(i, startX, endX);
                if (Math.abs(tx - thumbX) < gap + dp(1)) {
                    continue;
                }
                boolean isDefaultOne = (i == 5);
                float radius;
                int dotColor;

                if (isDefaultOne) {
                    radius = dp(3f);
                    dotColor = (tx < thumbX) ? colors.surfaceLow : colors.onSurface;
                } else {
                    radius = dp(1.8f);
                    dotColor = (tx < thumbX) ? colors.surfaceLow : ColorUtils.setAlphaComponent(colors.onSurfaceVariant, 0x66);
                }

                tickPaint.setColor(dotColor);
                canvas.drawCircle(tx, cy, radius, tickPaint);
            }

            float thumbW = dp(4.5f) + dp(1.5f) * thumbRadiusProgress;
            float thumbH = dp(38) + dp(6) * thumbRadiusProgress;
            float thumbR = thumbW / 2f;
            canvas.drawRoundRect(thumbX - thumbW / 2f, cy - thumbH / 2f, thumbX + thumbW / 2f, cy + thumbH / 2f, thumbR, thumbR, thumbPaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            float startX = getPaddingLeft() + dp(24);
            float endX = getWidth() - getPaddingRight() - dp(24);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (snapAnim != null) {
                        snapAnim.cancel();
                    }
                    isDragging = true;
                    animateThumbRadius(1f);
                    updatePosition(event.getX(), startX, endX);
                    return true;

                case MotionEvent.ACTION_MOVE:
                    updatePosition(event.getX(), startX, endX);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDragging = false;
                    animateThumbRadius(0f);
                    snapToStep(startX, endX);
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    return true;
            }
            return super.onTouchEvent(event);
        }

        private void updatePosition(float touchX, float startX, float endX) {
            float clampedX = Math.max(startX, Math.min(endX, touchX));
            thumbX = clampedX;

            float pct = (clampedX - startX) / (endX - startX);
            int newIndex = Math.max(0, Math.min(STEPS, Math.round(pct * STEPS)));

            if (newIndex != stepIndex) {
                stepIndex = newIndex;
                try {
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                } catch (Throwable ignored) {
                }
                float newSpeed = Math.round((MIN_SPEED + stepIndex * STEP_SIZE) * 10f) / 10f;
                if (listener != null) {
                    listener.onSpeedChanged(newSpeed);
                }
            }
            invalidate();
        }

        private void snapToStep(float startX, float endX) {
            float targetX = getXForIndex(stepIndex, startX, endX);
            if (snapAnim != null) {
                snapAnim.cancel();
            }
            snapAnim = ValueAnimator.ofFloat(thumbX, targetX);
            snapAnim.setDuration(160);
            snapAnim.setInterpolator(CubicBezierInterpolator.EASE_OUT);
            snapAnim.addUpdateListener(a -> {
                thumbX = (float) a.getAnimatedValue();
                invalidate();
            });
            snapAnim.start();
        }

        private void animateThumbRadius(float target) {
            if (thumbRadiusAnim != null) {
                thumbRadiusAnim.cancel();
            }
            thumbRadiusAnim = ValueAnimator.ofFloat(thumbRadiusProgress, target);
            thumbRadiusAnim.setDuration(140);
            thumbRadiusAnim.setInterpolator(CubicBezierInterpolator.DEFAULT);
            thumbRadiusAnim.addUpdateListener(a -> {
                thumbRadiusProgress = (float) a.getAnimatedValue();
                invalidate();
            });
            thumbRadiusAnim.start();
        }
    }
}
