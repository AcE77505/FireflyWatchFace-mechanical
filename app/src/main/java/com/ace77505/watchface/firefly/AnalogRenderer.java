package com.ace77505.watchface.firefly;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.wear.watchface.CanvasType;
import androidx.wear.watchface.Renderer;
import androidx.wear.watchface.WatchState;
import androidx.wear.watchface.style.CurrentUserStyleRepository;
import java.time.ZonedDateTime;

public class AnalogRenderer extends Renderer.CanvasRenderer {
    public final Paint paint = new Paint();

    // ===== 颜色方案 =====
    public static final int COLOR_HOUR = Color.parseColor("#FFEB80"); // 时针
    public static final int COLOR_MINUTE = Color.parseColor("#bfe2a4"); // 新增：独立分针颜色，默认浅绿色
    public static final int COLOR_SECOND = Color.parseColor("#7ED9C7");      // 青绿色：秒针 + 12/3/6/9刻度
    public static final int COLOR_MAIN_TICK = Color.parseColor("#E8F4F2");   // 浅银白：其他主刻度
    public static final int COLOR_MINOR_TICK = Color.parseColor("#FFFFFF");  // 白色：副刻度
    public static final int COLOR_CENTER_RING = Color.parseColor("#64B6F7"); // 转轴外圈

    // ===== 针长统一缩放参数 =====
    public static final float HAND_LENGTH_SCALE = 0.90f;

    // 电量环对象
    public final BatteryRing batteryRing;

    public AnalogRenderer(
            SurfaceHolder surfaceHolder,
            CurrentUserStyleRepository currentUserStyleRepository,
            WatchState watchState,
            long frameDelayMillis,
            Context context
    ) {
        super(
                surfaceHolder,
                currentUserStyleRepository,
                watchState,
                CanvasType.HARDWARE,
                frameDelayMillis
        );

        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStrokeCap(Paint.Cap.ROUND); // 全局圆角线帽

        // 初始化电量环
        this.batteryRing = new BatteryRing(context);
    }

    @Override
    public void render(@NonNull Canvas canvas, @NonNull Rect bounds, @NonNull ZonedDateTime dateTime) {
        drawBackground(canvas);
        batteryRing.draw(canvas, bounds); // 使用独立的电量环类绘制
        drawTicks(canvas, bounds);
        drawHands(canvas, bounds, dateTime);
        drawCenterDecoration(canvas, bounds);
    }

    @Override
    public void renderHighlightLayer(@NonNull Canvas canvas, @NonNull Rect bounds, @NonNull ZonedDateTime dateTime) {
        // 暂不需要高亮层
    }

    public static void drawBackground(Canvas canvas) {
        canvas.drawColor(Color.BLACK); // 纯黑色背景
        // 氛围光已去除
    }

    public void drawTicks(Canvas canvas, Rect bounds) {
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.min(bounds.width(), bounds.height()) * 0.5f;

        // 调整刻度半径，避免与电量环重叠（改为2%内缩）
        float tickRadius = radius * 0.98f; // 从0.95f改为0.98f，内缩2%

        paint.setStyle(Paint.Style.STROKE);

        for (int i = 0; i < 60; i++) {
            boolean isHour = (i % 5 == 0);
            boolean isSpecialHour = (i == 0 || i == 15 || i == 30 || i == 45); // 12/3/6/9点

            float outerR = tickRadius * 0.97f;
            float innerR = isHour ? tickRadius * 0.85f : tickRadius * 0.90f; // 主刻度缩短
            float strokeWidth = isHour ? Math.max(2f, tickRadius * 0.01f) : Math.max(1f, tickRadius * 0.004f);

            paint.setStrokeWidth(strokeWidth);
            if (isSpecialHour) {
                paint.setColor(COLOR_SECOND); // 12/3/6/9统一青绿
                paint.setAlpha(255);
            } else if (isHour) {
                paint.setColor(COLOR_MAIN_TICK);
                paint.setAlpha(255);
            } else {
                paint.setColor(COLOR_MINOR_TICK);
                paint.setAlpha(200);
            }

            double angleRad = Math.toRadians(i * 6f);
            float sx = cx + (float) Math.sin(angleRad) * innerR;
            float sy = cy - (float) Math.cos(angleRad) * innerR;
            float ex = cx + (float) Math.sin(angleRad) * outerR;
            float ey = cy - (float) Math.cos(angleRad) * outerR;

            canvas.drawLine(sx, sy, ex, ey, paint);
        }

        paint.setAlpha(255);
    }

    public void drawHands(Canvas canvas, Rect bounds, ZonedDateTime time) {
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();

        float seconds = time.getSecond() + time.getNano() / 1_000_000_000f;
        float secAngle = seconds * 6f;
        float minAngle = time.getMinute() * 6f + seconds * 0.1f;
        float hourAngle = (time.getHour() % 12) * 30f + time.getMinute() * 0.5f;

        float maxDim = Math.min(bounds.width(), bounds.height());

        // ===== 基础长度（未缩放）=====
        float hourBaseLen = maxDim * 0.28f;
        float minBaseLen = maxDim * 0.38f;
        float secBaseLen = maxDim * 0.45f;

        // ===== 应用统一缩放参数 =====
        float hourLen = hourBaseLen * HAND_LENGTH_SCALE; // 最终 ≈ 0.24R
        float minLen = minBaseLen * HAND_LENGTH_SCALE;   // 最终 ≈ 0.32R
        float secLen = secBaseLen * HAND_LENGTH_SCALE;   // 最终 ≈ 0.38R

        // --- 时针：橙黄色，粗，圆角 ---
        paint.setColor(COLOR_HOUR);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(6f, maxDim * 0.02f));
        drawHand(canvas, cx, cy, hourAngle, hourLen);

        // --- 分针：独立浅绿色，中等粗细，圆角 ---
        paint.setColor(COLOR_MINUTE);
        paint.setStrokeWidth(Math.max(4f, maxDim * 0.012f));
        drawHand(canvas, cx, cy, minAngle, minLen);

        // --- 秒针：青绿色，细长，带纤细拖尾 ---
        paint.setColor(COLOR_SECOND);
        paint.setStrokeWidth(Math.max(2f, maxDim * 0.006f));
        paint.setStrokeCap(Paint.Cap.ROUND);

        canvas.save();
        canvas.rotate(secAngle, cx, cy);

        // 主干（向上）
        canvas.drawLine(cx, cy, cx, cy - secLen, paint);

        // 拖尾（向下，同粗细细线，长度=主干20%）
        float tailLen = secLen * 0.20f;
        canvas.drawLine(cx, cy, cx, cy + tailLen, paint);

        canvas.restore();
    }

    public void drawHand(Canvas canvas, float cx, float cy, float angle, float length) {
        canvas.save();
        canvas.rotate(angle, cx, cy);
        canvas.drawLine(cx, cy, cx, cy - length, paint);
        canvas.restore();
    }

    public void drawCenterDecoration(Canvas canvas, Rect bounds) {
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.min(bounds.width(), bounds.height()) * 0.5f;

        // ===== 缩小中心装饰尺寸 =====
        float centerBaseRadius = radius * 0.05f; // 原0.06 → 缩小17%

        // 外圈：蓝色环（修改此处）
        paint.setStyle(Paint.Style.STROKE);
        paint.setColor(COLOR_CENTER_RING); // 改为蓝色
        paint.setStrokeWidth(radius * 0.018f);
        canvas.drawCircle(cx, cy, centerBaseRadius * 1.25f, paint); // 原1.40 → 缩小至1.25

        // 内圈：橙黄色高光环（呼应指针色）
        paint.setColor(COLOR_HOUR);
        paint.setStrokeWidth(radius * 0.008f);
        canvas.drawCircle(cx, cy, centerBaseRadius * 1.10f, paint);

        // 中心盖：橙黄色实心圆
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_HOUR);
        canvas.drawCircle(cx, cy, centerBaseRadius, paint);
    }

}