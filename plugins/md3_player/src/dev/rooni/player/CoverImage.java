package dev.rooni.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.ui.Components.BackupImageView;

public class CoverImage extends BackupImageView {

    public interface Listener {
        void onSeed(MessageObject messageObject, int seed);
    }

    private final Paint placeholderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final PlayerIcon note;
    private int radius;
    private String currentKey;
    private MessageObject message;
    private int fallbackSeed;
    private Listener listener;

    public CoverImage(Context context, int noteSizeDp) {
        super(context);
        note = PlayerIcon.stroke(PlayerIcon.NOTE, noteSizeDp);
        try {
            getImageReceiver().setCrossfadeWithOldImage(true);
            getImageReceiver().setForceCrossfade(true);
            getImageReceiver().setCrossfadeDuration(260);
        } catch (Throwable ignored) {
        }
        getImageReceiver().setDelegate(new org.telegram.messenger.ImageReceiver.ImageReceiverDelegate() {
            @Override
            public void didSetImage(org.telegram.messenger.ImageReceiver imageReceiver, boolean set, boolean thumb, boolean memCache) {
                if (set) {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            onBitmap(imageReceiver.getBitmap());
                        } catch (Throwable ignored) {
                        }
                    });
                }
            }

            @Override
            public void didSetImageBitmap(int type, String key, android.graphics.drawable.Drawable drawable) {
                if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            onBitmap(((android.graphics.drawable.BitmapDrawable) drawable).getBitmap());
                        } catch (Throwable ignored) {
                        }
                    });
                }
            }

            @Override
            public void onAnimationReady(org.telegram.messenger.ImageReceiver imageReceiver) {
            }
        });
    }

    public void setListener(int fallbackSeed, Listener listener) {
        this.fallbackSeed = fallbackSeed;
        this.listener = listener;
    }

    public void setRadius(int r) {
        radius = r;
        setRoundRadius(r);
        invalidate();
    }

    public void setColors(int background, int foreground) {
        placeholderPaint.setColor(background);
        note.setColor(foreground);
        invalidate();
    }

    public MessageObject getMessage() {
        return message;
    }

    public void setMessage(MessageObject messageObject) {
        if (messageObject == null) {
            currentKey = null;
            message = null;
            setImageDrawable(null);
            return;
        }
        Bitmap file = PlayerArt.fileCover(messageObject);
        String key = PlayerArt.key(messageObject) + (file != null ? ":f" : "");
        if (key.equals(currentKey)) {
            return;
        }
        currentKey = key;
        message = messageObject;

        Bitmap oldBitmap = null;
        try {
            oldBitmap = getImageReceiver().getBitmap();
        } catch (Throwable ignored) {
        }

        if (file != null) {
            if (oldBitmap != null && !oldBitmap.isRecycled()) {
                try {
                    getImageReceiver().startCrossfadeFromStaticThumb(oldBitmap);
                } catch (Throwable ignored) {
                }
            }
            setImageBitmap(file);
            onBitmap(file);
            return;
        }
        ImageLocation full = PlayerArt.fullLocation(messageObject);
        ImageLocation thumb = PlayerArt.thumbLocation(messageObject);
        if (oldBitmap != null && !oldBitmap.isRecycled()) {
            try {
                getImageReceiver().startCrossfadeFromStaticThumb(oldBitmap);
            } catch (Throwable ignored) {
            }
        }
        try {
            if (full != null) {
                setImage(full, null, thumb, null, null, 0L, 1, messageObject);
            } else if (thumb != null) {
                setImage(null, null, thumb, null, null, 0L, 1, messageObject);
            } else {
                setImageDrawable(null);
            }
        } catch (Throwable t) {
            try {
                setImageDrawable(null);
            } catch (Throwable ignored) {
            }
        }
    }

    private void onBitmap(Bitmap bitmap) {
        final MessageObject target = message;
        if (bitmap == null || target == null || listener == null) {
            return;
        }
        PlayerArt.requestSeed(target, bitmap, fallbackSeed, seed -> {
            if (target == message && listener != null) {
                listener.onSeed(target, seed);
            }
        });
    }

    @Override
    public void onDraw(Canvas canvas) {
        boolean hasImage = getImageReceiver().hasBitmapImage() || getImageReceiver().getThumbBitmap() != null;
        if (!hasImage) {
            rect.set(0, 0, getWidth(), getHeight());
            canvas.drawRoundRect(rect, radius, radius, placeholderPaint);
            note.setBounds(0, 0, getWidth(), getHeight());
            note.draw(canvas);
        }
        super.onDraw(canvas);
    }
}
