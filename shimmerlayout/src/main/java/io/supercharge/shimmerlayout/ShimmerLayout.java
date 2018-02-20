package io.supercharge.shimmerlayout;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.widget.FrameLayout;

public class ShimmerLayout extends FrameLayout {

    private static final int DEFAULT_ANIMATION_DURATION = 1500;
    private static final int DEFAULT_ANIMATION_DELAY = 0;
    private static final int DEFAULT_ANGLE = 20;
    private static final int MIN_ANGLE_VALUE = 0;
    private static final int MAX_ANGLE_VALUE = 30;
    private static final int MIN_MASK_WIDTH_VALUE = 0;
    private static final int MAX_MASK_WIDTH_VALUE = 1;
    private static final int MIN_GRADIENT_CENTER_COLOR_WIDTH_VALUE = 0;
    private static final int MAX_GRADIENT_CENTER_COLOR_WIDTH_VALUE = 1;

    private int mask1OffsetX;
    private int mask2OffsetX;
    private Rect maskRect;
    private Paint gradientTexturePaint1;
    private Paint gradientTexturePaint2;
    private AnimatorSet maskAnimator;

    private Bitmap localMask1Bitmap;
    private Bitmap localMask2Bitmap;
    private Bitmap mask1Bitmap;
    private Bitmap mask2Bitmap;
    private Canvas canvasForShimmerMask1;
    private Canvas canvasForShimmerMask2;

    private boolean isAnimationStarted;
    private boolean autoStart;
    private int shimmerAnimationDuration;
    private int shimmerAnimationDelay;
    private int shimmerColor;
    private int shimmerAngle;
    private float maskWidth;
    private float gradientCenterColorWidth;
    private boolean shimmerEchoEnabled;

    private ViewTreeObserver.OnPreDrawListener startAnimationPreDrawListener;

    public ShimmerLayout(Context context) {
        this(context, null);
    }

    public ShimmerLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ShimmerLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        setWillNotDraw(false);

        mask1OffsetX = Integer.MIN_VALUE;
        mask2OffsetX = Integer.MIN_VALUE;

        TypedArray a = context.getTheme().obtainStyledAttributes(
                attrs,
                R.styleable.ShimmerLayout,
                0, 0);

        try {
            shimmerAngle = a.getInteger(R.styleable.ShimmerLayout_shimmer_angle, DEFAULT_ANGLE);
            shimmerAnimationDuration = a.getInteger(R.styleable.ShimmerLayout_shimmer_animation_duration, DEFAULT_ANIMATION_DURATION);
            shimmerAnimationDelay = a.getInteger(R.styleable.ShimmerLayout_shimmer_animation_delay, DEFAULT_ANIMATION_DELAY);
            shimmerColor = a.getColor(R.styleable.ShimmerLayout_shimmer_color, getColor(R.color.shimmer_color));
            autoStart = a.getBoolean(R.styleable.ShimmerLayout_shimmer_auto_start, false);
            maskWidth = a.getFloat(R.styleable.ShimmerLayout_shimmer_mask_width, 0.5F);
            gradientCenterColorWidth = a.getFloat(R.styleable.ShimmerLayout_shimmer_gradient_center_color_width, 0.1F);
            shimmerEchoEnabled = a.getBoolean(R.styleable.ShimmerLayout_shimmer_echo_enabled, true);
        } finally {
            a.recycle();
        }

        setMaskWidth(maskWidth);
        setGradientCenterColorWidth(gradientCenterColorWidth);
        setShimmerAngle(shimmerAngle);
        if (autoStart && getVisibility() == VISIBLE) {
            startShimmerAnimation();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        resetShimmering();
        super.onDetachedFromWindow();
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (!isAnimationStarted || getWidth() <= 0 || getHeight() <= 0 || (mask1OffsetX == Integer.MIN_VALUE && mask2OffsetX == Integer.MIN_VALUE)) {
            super.dispatchDraw(canvas);
        } else {
            super.dispatchDraw(canvas);

            if (mask1OffsetX != Integer.MIN_VALUE) {
                dispatchDrawShimmer1(canvas);
            }
            if (mask2OffsetX != Integer.MIN_VALUE) {
                dispatchDrawShimmer2(canvas);
            }
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            if (autoStart) {
                startShimmerAnimation();
            }
        } else {
            stopShimmerAnimation();
        }
    }

    public void startShimmerAnimation() {
        if (isAnimationStarted) {
            return;
        }

        if (getWidth() == 0) {
            startAnimationPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    getViewTreeObserver().removeOnPreDrawListener(this);
                    startShimmerAnimation();

                    return true;
                }
            };

            getViewTreeObserver().addOnPreDrawListener(startAnimationPreDrawListener);

            return;
        }

        Animator animator = getShimmerAnimation();
        animator.start();
        isAnimationStarted = true;
    }

    public void stopShimmerAnimation() {
        if (startAnimationPreDrawListener != null) {
            getViewTreeObserver().removeOnPreDrawListener(startAnimationPreDrawListener);
        }

        resetShimmering();
    }

    public void setShimmerColor(int shimmerColor) {
        this.shimmerColor = shimmerColor;
        resetIfStarted();
    }

    public void setShimmerAnimationDuration(int durationMillis) {
        this.shimmerAnimationDuration = durationMillis;
        resetIfStarted();
    }

    public void setShimmerAnimationDelay(int delayMillis) {
        this.shimmerAnimationDelay = delayMillis;
        resetIfStarted();
    }

    public void enableShimmerEcho(boolean enabled) {
        this.shimmerEchoEnabled = enabled;
        resetIfStarted();
    }

    /**
     * Set the angle of the shimmer effect in clockwise direction in degrees.
     * The angle must be between {@value #MIN_ANGLE_VALUE} and {@value #MAX_ANGLE_VALUE}.
     *
     * @param angle The angle to be set
     */
    public void setShimmerAngle(int angle) {
        if (angle < MIN_ANGLE_VALUE || MAX_ANGLE_VALUE < angle) {
            throw new IllegalArgumentException(String.format("shimmerAngle value must be between %d and %d",
                    MIN_ANGLE_VALUE,
                    MAX_ANGLE_VALUE));
        }
        this.shimmerAngle = angle;
        resetIfStarted();
    }

    /**
     * Sets the width of the shimmer line to a value higher than 0 to less or equal to 1.
     * 1 means the width of the shimmer line is equal to half of the width of the ShimmerLayout.
     * The default value is 0.5.
     *
     * @param maskWidth The width of the shimmer line.
     */
    public void setMaskWidth(float maskWidth) {
        if (maskWidth <= MIN_MASK_WIDTH_VALUE || MAX_MASK_WIDTH_VALUE < maskWidth) {
            throw new IllegalArgumentException(String.format("maskWidth value must be higher than %d and less or equal to %d",
                    MIN_MASK_WIDTH_VALUE, MAX_MASK_WIDTH_VALUE));
        }

        this.maskWidth = maskWidth;
        resetIfStarted();
    }

    /**
     * Sets the width of the center gradient color to a value higher than 0 to less than 1.
     * 0.99 means that the whole shimmer line will have this color with a little transparent edges.
     * The default value is 0.1.
     *
     * @param gradientCenterColorWidth The width of the center gradient color.
     */
    public void setGradientCenterColorWidth(float gradientCenterColorWidth) {
        if (gradientCenterColorWidth <= MIN_GRADIENT_CENTER_COLOR_WIDTH_VALUE
                || MAX_GRADIENT_CENTER_COLOR_WIDTH_VALUE <= gradientCenterColorWidth) {
            throw new IllegalArgumentException(String.format("gradientCenterColorWidth value must be higher than %d and less than %d",
                    MIN_GRADIENT_CENTER_COLOR_WIDTH_VALUE, MAX_GRADIENT_CENTER_COLOR_WIDTH_VALUE));
        }

        this.gradientCenterColorWidth = gradientCenterColorWidth;
        resetIfStarted();
    }

    private void resetIfStarted() {
        if (isAnimationStarted) {
            resetShimmering();
            startShimmerAnimation();
        }
    }

    private void dispatchDrawShimmer1(Canvas canvas) {
        if (mask1Bitmap == null) {
            mask1Bitmap = createBitmap(maskRect.width(), getHeight());
        }

        localMask1Bitmap = mask1Bitmap;
        if (localMask1Bitmap == null) {
            return;
        }

        if (canvasForShimmerMask1 == null) {
            canvasForShimmerMask1 = new Canvas(localMask1Bitmap);
        }

        canvasForShimmerMask1.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        canvasForShimmerMask1.save();
        canvasForShimmerMask1.translate(-mask1OffsetX, 0);

        super.dispatchDraw(canvasForShimmerMask1);

        canvasForShimmerMask1.restore();

        if (gradientTexturePaint1 == null) {
            gradientTexturePaint1 = createShimmerPaint(localMask1Bitmap, shimmerColor);
        }

        drawShimmer(canvas, mask1OffsetX, gradientTexturePaint1);

        localMask1Bitmap = null;
    }


    private void dispatchDrawShimmer2(Canvas canvas) {
        if (mask2Bitmap == null) {
            mask2Bitmap = createBitmap(maskRect.width(), getHeight());
        }

        localMask2Bitmap = mask2Bitmap;
        if (localMask2Bitmap == null) {
            return;
        }

        if (canvasForShimmerMask2 == null) {
            canvasForShimmerMask2 = new Canvas(localMask2Bitmap);
        }

        canvasForShimmerMask2.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        canvasForShimmerMask2.save();
        canvasForShimmerMask2.translate(-mask2OffsetX, 0);

        super.dispatchDraw(canvasForShimmerMask2);

        canvasForShimmerMask2.restore();

        if (gradientTexturePaint2 == null) {
            gradientTexturePaint2 = createShimmerPaint(localMask2Bitmap, adjustAlpha(shimmerColor, 0.5f));
        }

        drawShimmer(canvas, mask2OffsetX, gradientTexturePaint2);

        localMask1Bitmap = null;
    }

    private void drawShimmer(Canvas destinationCanvas, float offset, Paint paint) {
        destinationCanvas.save();
        destinationCanvas.translate(offset, 0);
        destinationCanvas.drawRect(maskRect.left, 0, maskRect.width(), maskRect.height(), paint);
        destinationCanvas.restore();
    }

    private void resetShimmering() {
        if (maskAnimator != null) {
            maskAnimator.end();
            maskAnimator.removeAllListeners();
            for (Animator child : maskAnimator.getChildAnimations()) {
                child.removeAllListeners();
                if (child instanceof ValueAnimator) {
                    ((ValueAnimator)child).removeAllUpdateListeners();
                }
            }
        }

        maskAnimator = null;
        gradientTexturePaint1 = null;
        gradientTexturePaint2 = null;
        isAnimationStarted = false;
        releaseBitMaps();
    }

    private void releaseBitMaps() {
        canvasForShimmerMask1 = null;
        canvasForShimmerMask2 = null;

        if (mask1Bitmap != null) {
            mask1Bitmap.recycle();
            mask1Bitmap = null;
        }
        if (mask2Bitmap != null) {
            mask2Bitmap.recycle();
            mask2Bitmap = null;
        }
    }

    private Paint createShimmerPaint(Bitmap localBitmap, int color) {
        final int edgeColor = adjustAlpha(color, 0);
        final float shimmerLineWidth = getWidth() / 2 * maskWidth;

        LinearGradient gradient = new LinearGradient(
                0, getHeight(),
                (int) (Math.cos(Math.toRadians(shimmerAngle)) * shimmerLineWidth),
                getHeight() + (int) (Math.sin(Math.toRadians(shimmerAngle)) * shimmerLineWidth),
                new int[]{edgeColor, color, color, edgeColor},
                getGradientColorDistribution(),
                Shader.TileMode.CLAMP);

        BitmapShader maskBitmapShader = new BitmapShader(localBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);

        ComposeShader composeShader = new ComposeShader(gradient, maskBitmapShader, PorterDuff.Mode.DST_IN);

        Paint gradientTexturePaint = new Paint();
        gradientTexturePaint.setAntiAlias(true);
        gradientTexturePaint.setDither(true);
        gradientTexturePaint.setFilterBitmap(true);
        gradientTexturePaint.setShader(composeShader);
        return gradientTexturePaint;
    }

    private Animator getShimmerAnimation() {
        if (maskAnimator != null) {
            return maskAnimator;
        }

        if (maskRect == null) {
            maskRect = calculateBitmapMaskRect();
        }

        final int animationToX = getWidth();
        final int animationFromX;

        if (getWidth() > maskRect.width()) {
            animationFromX = -animationToX;
        } else {
            animationFromX = -maskRect.width();
        }

        final int shimmerBitmapWidth = maskRect.width();
        final int shimmerAnimationFullLength = animationToX - animationFromX;

        maskAnimator = new AnimatorSet();

        ValueAnimator delay = ValueAnimator.ofFloat(0.0F, 1.0F);
        delay.setDuration(shimmerAnimationDelay);

        ValueAnimator shim1Animator = ValueAnimator.ofFloat(0.0F, 1.0F);
        shim1Animator.setDuration(shimmerAnimationDuration);

        final float[] value1 = new float[1];
        shim1Animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                value1[0] = (Float) animation.getAnimatedValue();
                mask1OffsetX = ((int) (animationFromX + shimmerAnimationFullLength * value1[0]));

                if (mask1OffsetX + shimmerBitmapWidth >= 0) {
                    invalidate();
                }
            }
        });

        ValueAnimator shim2Animator = ValueAnimator.ofFloat(0.0F, 1.0F);
        shim2Animator.setInterpolator(new AccelerateInterpolator(2));
        shim2Animator.setDuration(shimmerAnimationDuration - shimmerAnimationDuration/10);

        final float[] value2 = new float[1];
        shim2Animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                value2[0] = (Float) animation.getAnimatedValue();
                mask2OffsetX = ((int) (animationFromX + shimmerAnimationFullLength * value2[0]));

                if (mask2OffsetX + shimmerBitmapWidth >= 0) {
                    invalidate();
                }
            }
        });

        maskAnimator.play(shim1Animator).after(delay);
        if (shimmerEchoEnabled) {
            maskAnimator.play(shim2Animator).with(shim1Animator);
        }

        maskAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean mCanceled;

            @Override
            public void onAnimationStart(Animator animation) {
                mCanceled = false;
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mCanceled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mask1OffsetX = Integer.MIN_VALUE;
                mask2OffsetX = Integer.MIN_VALUE;
                if (!mCanceled) {
                    animation.start();
                }
            }

        });

        return maskAnimator;
    }

    private Bitmap createBitmap(int width, int height) {
        try {
            return Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8);
        } catch (OutOfMemoryError e) {
            System.gc();

            return null;
        }
    }

    private int getColor(int id) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getContext().getColor(id);
        } else {
            //noinspection deprecation
            return getResources().getColor(id);
        }
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private Rect calculateBitmapMaskRect() {
        return new Rect(0, 0, calculateMaskWidth(), getHeight());
    }

    private int calculateMaskWidth() {
        final double shimmerLineBottomWidth = (getWidth() / 2 * maskWidth) / Math.cos(Math.toRadians(shimmerAngle));
        final double shimmerLineRemainingTopWidth = getHeight() * Math.tan(Math.toRadians(shimmerAngle));

        return (int) (shimmerLineBottomWidth + shimmerLineRemainingTopWidth);
    }

    private float[] getGradientColorDistribution() {
        final float[] colorDistribution = new float[4];

        colorDistribution[0] = 0;
        colorDistribution[3] = 1;

        colorDistribution[1] = 0.5F - gradientCenterColorWidth / 2F;
        colorDistribution[2] = 0.5F + gradientCenterColorWidth / 2F;

        return colorDistribution;
    }
}
