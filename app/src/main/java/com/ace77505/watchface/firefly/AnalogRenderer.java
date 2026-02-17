package com.ace77505.watchface.firefly;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.BatteryManager;
import android.view.SurfaceHolder;

import androidx.annotation.NonNull;
import androidx.wear.watchface.CanvasType;
import androidx.wear.watchface.Renderer;
import androidx.wear.watchface.WatchState;
import androidx.wear.watchface.style.CurrentUserStyleRepository;
import java.time.ZonedDateTime;

public class AnalogRenderer extends Renderer.CanvasRenderer {
    public final Paint paint = new Paint();

    // Context 用于获取电量
    public final Context context;

    // ===== 颜色方案（已交换：时/分针用橙黄，转轴外圈用浅金）=====
    public static final int COLOR_HOUR = Color.parseColor("#FFEB80"); // 金黄色：时针
    public static final int COLOR_MINUTE = Color.parseColor("#bfe2a4"); // 新增：独立分针颜色，默认浅绿色

    public static final int COLOR_SECOND = Color.parseColor("#7ED9C7");      // 青绿色：秒针 + 12/3/6/9刻度
    public static final int COLOR_MAIN_TICK = Color.parseColor("#E8F4F2");   // 浅银白：其他主刻度
    public static final int COLOR_MINOR_TICK = Color.parseColor("#FFFFFF");  // 白色：副刻度

    public static final int COLOR_CENTER_RING = Color.parseColor("#64B6F7"); // 转轴外圈

    // ===== 电量环颜色（固定为黄色）=====
    public static final int COLOR_BATTERY_RING = Color.parseColor("#FFA04A"); // 电量环固定为黄色

    // ===== 电量环厚度（占半径比例）=====
    public static final float BATTERY_RING_THICKNESS_RATIO = 0.0125f; // 厚度 = 0.0125R（更细）

    // ===== 电量环内缩比例（新增）=====
    public static final float BATTERY_RING_INSET_RATIO = 0.96f; // 内缩

    // ===== 是否显示电量环背景（新增）=====
    public static final boolean SHOW_BATTERY_RING_BACKGROUND = false; // 设为false表示透明背景

    // ===== 针长统一缩放参数 =====
    public static final float HAND_LENGTH_SCALE = 0.90f; // 主推值：整体缩短15%


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
        this.context = context.getApplicationContext();
        paint.setAntiAlias(true);
        paint.setDither(true);
        paint.setStrokeCap(Paint.Cap.ROUND); // 全局圆角线帽
    }

    @Override
    public void render(@NonNull Canvas canvas, @NonNull Rect bounds, @NonNull ZonedDateTime dateTime) {
        drawBackground(canvas);
        drawBatteryRing(canvas, bounds); // 新增：绘制电量环
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

    // ===== 新增：绘制电量环方法（使用真实电量） =====
    public void drawBatteryRing(Canvas canvas, Rect bounds) {
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.min(bounds.width(), bounds.height()) * 0.5f;

        // 获取当前电量（0..1）
        float batteryLevel = getBatteryLevel();
        batteryLevel = Math.max(0f, Math.min(1f, batteryLevel));

        float outerRadius = radius * BATTERY_RING_INSET_RATIO;
        float ringThickness = radius * BATTERY_RING_THICKNESS_RATIO;
        float ringCenterRadius = outerRadius - ringThickness / 2f;

        // 绘制电量环背景（如果需要）
        if (SHOW_BATTERY_RING_BACKGROUND) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ringThickness);
            paint.setColor(Color.parseColor("#333333"));
            paint.setAlpha(150);
            canvas.drawCircle(cx, cy, ringCenterRadius, paint);
        }

        // 绘制电量填充部分
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(ringThickness);
        paint.setColor(COLOR_BATTERY_RING);
        paint.setAlpha(255);

        float startAngle = -90f;
        float sweepAngle = 360f * batteryLevel;

        Paint.Cap prevCap = paint.getStrokeCap();
        paint.setStrokeCap(Paint.Cap.BUTT);

        canvas.drawArc(
                cx - ringCenterRadius,
                cy - ringCenterRadius,
                cx + ringCenterRadius,
                cy + ringCenterRadius,
                startAngle,
                sweepAngle,
                false,
                paint
        );

        paint.setStrokeCap(prevCap);

        // 绘制电量环终点燃烧特效
        if (batteryLevel > 0f && sweepAngle > 0f) {
            double endAngleRad = Math.toRadians(startAngle + sweepAngle);
            float dotX = cx + (float) Math.cos(endAngleRad) * ringCenterRadius;
            float dotY = cy + (float) Math.sin(endAngleRad) * ringCenterRadius;

            // 使用 FlameEffect 类绘制燃烧特效
            FlameEffect.draw(canvas, paint, dotX, dotY, radius, batteryLevel);
        }

        paint.setStyle(Paint.Style.STROKE);
    }

    /**
     * 返回当前电量，范围 0..1
     * 优先使用 BatteryManager.BATTERY_PROPERTY_CAPACITY（API 21+），若不可用则使用 ACTION_BATTERY_CHANGED 的粘性 Intent 计算（level/scale）
     * 若两者均失败，则回退到 0.75（保守默认值）
     */
    public float getBatteryLevel() {
        try {
            // 优先使用 BatteryManager 的 BATTERY_PROPERTY_CAPACITY（百分比，0..100）
            BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                int capacity = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                if (capacity > 0 && capacity <= 100) {
                    return capacity / 100f;
                }
            }

            // 回退：使用粘性广播 Intent.ACTION_BATTERY_CHANGED
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    return (float) level / (float) scale;
                }
            }
        } catch (Exception e) {
            // 安全降级：不抛异常，仅使用默认值
        }

        // 最后回退默认值（可根据需要改为 0f）
        return 0.75f;
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