package com.fox.music.feature.player.lyric.ui;

import static com.blankj.utilcode.util.SizeUtils.dp2px;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.blankj.utilcode.util.ScreenUtils;

/**
 * 支持WindowManager拖拽和点击事件的自定义View
 */
public class DesktopDragView extends CardView {

    // WindowManager相关参数
    private WindowManager.LayoutParams mWindowParams;
    private WindowManager mWindowManager;
    private boolean mIsAttachedToWindowManager = false;

    // 拖拽相关
    private boolean mIsDragEnabled = true;
    private boolean mAutoAttachToEdge = true;
    private int mDragLeftPadding = 0;
    private int mDragTopPadding = 0;
    private int mDragRightPadding = 0;
    private int mDragBottomPadding = 0;

    // 屏幕尺寸
    private int mScreenWidth;
    private int mScreenHeight;

    // 触摸相关
    private float mLastTouchX;
    private float mLastTouchY;
    private float mStartTouchX;
    private float mStartTouchY;
    private float mStartX;
    private float mStartY;
    private int mTouchSlop;
    private boolean mIsDragging = false;

    public DesktopDragView(Context context) {
        super(context);
        init();
    }

    public DesktopDragView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DesktopDragView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mScreenWidth = ScreenUtils.getScreenWidth();
        mScreenHeight = ScreenUtils.getScreenHeight();

        // 获取系统触摸阈值
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();

        // 确保可以接收点击事件
        setClickable(true);
        setFocusable(true);
    }

    // ----------------------------- WindowManager 相关 -----------------------------

    /**
     * 设置WindowManager和参数
     */
    public void setupWindowManager(WindowManager windowManager, WindowManager.LayoutParams params) {
        this.mWindowManager = windowManager;
        this.mWindowParams = params;
        this.mIsAttachedToWindowManager = true;

        // 记录初始位置
        if (params != null) {
            mStartX = params.x;
            mStartY = params.y;
        }
    }

    /**
     * 获取当前Window位置
     */
    public float[] getWindowPosition() {
        if (mWindowParams != null) {
            return new float[]{mWindowParams.x, mWindowParams.y};
        }
        return new float[]{getX(), getY()};
    }

    /**
     * 更新Window位置
     */
    public void updateWindowPosition(float x, float y) {
        if (mIsAttachedToWindowManager && mWindowManager != null && mWindowParams != null) {
            // 边界检查
            int[] clamped = clampPosition((int)x, (int)y);
            mWindowParams.x = clamped[0];
            mWindowParams.y = clamped[1];

            try {
                mWindowManager.updateViewLayout(this, mWindowParams);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            // 普通ViewGroup模式
            setX(x);
            setY(y);
        }
    }

    /**
     * 边界限制
     */
    private int[] clampPosition(int x, int y) {
        if (!mIsAttachedToWindowManager) {
            return new int[]{x, y};
        }

        // 计算View的宽高
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int viewWidth = getMeasuredWidth();
        int viewHeight = getMeasuredHeight();

        // 边界限制
        int minX = mDragLeftPadding;
        int minY = mDragTopPadding;
        int maxX = mScreenWidth - viewWidth - mDragRightPadding;
        int maxY = mScreenHeight - viewHeight - mDragBottomPadding;

        int clampedX = Math.max(minX, Math.min(x, maxX));
        int clampedY = Math.max(minY, Math.min(y, maxY));

        return new int[]{clampedX, clampedY};
    }

    // ----------------------------- 配置方法 -----------------------------

    public void setAutoAttach(boolean autoAttach) {
        this.mAutoAttachToEdge = autoAttach;
    }

    public void setDraggable(boolean draggable) {
        this.mIsDragEnabled = draggable;
    }

    public void setDragPadding(int left, int top, int right, int bottom) {
        this.mDragLeftPadding = dp2px(left);
        this.mDragTopPadding = dp2px(top);
        this.mDragRightPadding = dp2px(right);
        this.mDragBottomPadding = dp2px(bottom);
    }

    // ----------------------------- 触摸事件 -----------------------------

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mIsDragEnabled) {
            return super.onTouchEvent(event);
        }

        float rawX = event.getRawX();
        float rawY = event.getRawY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                // 记录初始位置
                mLastTouchX = rawX;
                mLastTouchY = rawY;
                mStartTouchX = rawX;
                mStartTouchY = rawY;
                mIsDragging = false;

                if (mIsAttachedToWindowManager && mWindowParams != null) {
                    mStartX = mWindowParams.x;
                    mStartY = mWindowParams.y;
                } else {
                    mStartX = getX();
                    mStartY = getY();
                }

                // 返回true表示我们要处理这个触摸序列
                return true;

            case MotionEvent.ACTION_MOVE:
                // 计算移动距离
                float moveDistance = Math.abs(rawX - mStartTouchX) + Math.abs(rawY - mStartTouchY);

                // 如果移动距离超过阈值，开始拖拽
                if (!mIsDragging && moveDistance > mTouchSlop) {
                    mIsDragging = true;
                }

                if (mIsDragging) {
                    // 计算新位置
                    float newX = mStartX + (rawX - mStartTouchX);
                    float newY = mStartY + (rawY - mStartTouchY);

                    // 更新位置
                    updateWindowPosition(newX, newY);

                    // 更新最后触摸点
                    mLastTouchX = rawX;
                    mLastTouchY = rawY;

                    // 拖拽时返回true，消费事件
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                // 判断是点击还是拖拽
                float upMoveDistance = Math.abs(rawX - mStartTouchX) + Math.abs(rawY - mStartTouchY);

                if (mIsDragging || upMoveDistance > mTouchSlop) {
                    // 是拖拽
                    if (mAutoAttachToEdge) {
                        performAutoAttach();
                    }
                    mIsDragging = false;
                    return true;
                } else {
                    // 是点击，触发点击事件
                    mIsDragging = false;
                    return performClick();
                }
        }

        // 默认情况下调用父类方法
        return super.onTouchEvent(event);
    }

    @Override
    public boolean performClick() {
        // 调用父类的performClick，这会触发OnClickListener
        super.performClick();
        return true;
    }

    /**
     * 自动贴边效果
     */
    private void performAutoAttach() {
        if (!mIsAttachedToWindowManager || mWindowParams == null) {
            return;
        }

        int currentX = mWindowParams.x;
        measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int viewWidth = getMeasuredWidth();
        int viewHeight = getMeasuredHeight();

        int screenCenterX = mScreenWidth / 2;
        int targetX;

        if (currentX + viewWidth / 2 < screenCenterX) {
            // 向左贴边
            targetX = mDragLeftPadding;
        } else {
            // 向右贴边
            targetX = mScreenWidth - viewWidth - mDragRightPadding;
        }

        // 使用动画平滑移动到目标位置
        animateToPosition(targetX, mWindowParams.y);
    }

    /**
     * 平滑移动到指定位置
     */
    private void animateToPosition(int targetX, int targetY) {
        if (!mIsAttachedToWindowManager || mWindowParams == null) {
            return;
        }

        // 创建值动画
        android.animation.ValueAnimator animatorX = android.animation.ValueAnimator.ofInt(mWindowParams.x, targetX);
        android.animation.ValueAnimator animatorY = android.animation.ValueAnimator.ofInt(mWindowParams.y, targetY);

        final int duration = 300;

        animatorX.setDuration(duration);
        animatorX.addUpdateListener(animation -> {
            int x = (int) animation.getAnimatedValue();
            if (mWindowParams != null) {
                mWindowParams.x = x;
                try {
                    mWindowManager.updateViewLayout(this, mWindowParams);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        animatorY.setDuration(duration);
        animatorY.addUpdateListener(animation -> {
            int y = (int) animation.getAnimatedValue();
            if (mWindowParams != null) {
                mWindowParams.y = y;
                try {
                    mWindowManager.updateViewLayout(this, mWindowParams);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        animatorX.start();
        animatorY.start();
    }

    // 移除旧的代理接口，使用新的方式
    private OnPositionChangedListener mPositionListener;

    public void setOnPositionChangedListener(OnPositionChangedListener listener) {
        this.mPositionListener = listener;
    }

    public interface OnPositionChangedListener {
        void onPositionChanged(float x, float y);
    }

    public void setTouchAble(boolean touchAble){
        try {
            mWindowParams.flags = touchAble ? WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE : WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            mWindowManager.removeView(this);
            mWindowManager.addView(this, mWindowParams);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}