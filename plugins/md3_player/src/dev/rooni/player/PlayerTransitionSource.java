package dev.rooni.player;

import android.graphics.Bitmap;
import android.graphics.RectF;

public interface PlayerTransitionSource {

    boolean canTransition();

    void getCardRect(RectF out);

    void getCoverRect(RectF out);

    float getCardRadius();

    float getCoverRadius();

    int getCardColor();

    Bitmap getCoverBitmap();

    Bitmap captureCard();

    void setTransitionHidden(boolean hidden);
}
