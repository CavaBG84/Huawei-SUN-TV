package com.huaweitv.solar.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import com.huaweitv.solar.R;
import com.huaweitv.solar.model.SolarTelemetry;

import java.util.Locale;

/**
 * Pure Java Hardware-Accelerated Energy Flow View for Android TV.
 * Compatible with Android 4.4.4 (API 19) through Android 15.
 */
public class EnergyFlowView extends View {

    private SolarTelemetry solarData = new SolarTelemetry();
    private float animPhase = 0f;

    // Palette
    private final int colorBgCard = Color.parseColor("#162032");
    private final int colorBorderCard = Color.parseColor("#2B3A55");
    private final int colorSolar = Color.parseColor("#F59E0B");
    private final int colorHouse = Color.parseColor("#38BDF8");
    private final int colorBatteryGreen = Color.parseColor("#10B981");
    private final int colorBatteryCyan = Color.parseColor("#06B6D4");
    private final int colorGridPurple = Color.parseColor("#8B5CF6");
    private final int colorGridRed = Color.parseColor("#EF4444");
    private final int colorTextPrimary = Color.parseColor("#F8FAFC");
    private final int colorTextSecondary = Color.parseColor("#94A3B8");
    private final int colorTextMuted = Color.parseColor("#64748B");

    private final Paint cardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cardBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particleGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textValuePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textSubtitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint iconFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float density;
    private float cardCornerRadius;

    private static final int ICON_SUN = 1;
    private static final int ICON_HOME = 2;
    private static final int ICON_BATTERY = 3;
    private static final int ICON_GRID = 4;

    public EnergyFlowView(Context context) {
        super(context);
        init();
    }

    public EnergyFlowView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EnergyFlowView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setLayerType(LAYER_TYPE_HARDWARE, null);
        density = getResources().getDisplayMetrics().density;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        cardCornerRadius = dp(14);

        cardPaint.setStyle(Paint.Style.FILL);
        cardBorderPaint.setStyle(Paint.Style.STROKE);
        cardBorderPaint.setStrokeWidth(dp(1.5f));

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.5f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(6)}, 0f));

        particlePaint.setStyle(Paint.Style.FILL);
        particleGlowPaint.setStyle(Paint.Style.FILL);

        textTitlePaint.setColor(colorTextSecondary);
        textTitlePaint.setTextSize(13 * scaledDensity);
        textTitlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        textValuePaint.setColor(colorTextPrimary);
        textValuePaint.setTextSize(28 * scaledDensity);
        textValuePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));

        textSubtitlePaint.setTextSize(12 * scaledDensity);
        textSubtitlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));

        iconPaint.setStyle(Paint.Style.STROKE);
        iconPaint.setStrokeWidth(dp(2f));
        iconPaint.setStrokeCap(Paint.Cap.ROUND);

        iconFillPaint.setStyle(Paint.Style.FILL);
    }

    public void setSolarData(SolarTelemetry data) {
        this.solarData = data;
        postInvalidate();
    }

    private float dp(float val) {
        return val * density;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float paddingH = dp(20);
        float paddingV = dp(14);

        float cardW = (w - paddingH * 2 - dp(24)) / 2f;
        float cardH = (h - paddingV * 2 - dp(20)) / 2f;

        RectF pvRect = new RectF(paddingH, paddingV, paddingH + cardW, paddingV + cardH);
        RectF homeRect = new RectF(w - paddingH - cardW, paddingV, w - paddingH, paddingV + cardH);
        RectF batteryRect = new RectF(paddingH, h - paddingV - cardH, paddingH + cardW, h - paddingV);
        RectF gridRect = new RectF(w - paddingH - cardW, h - paddingV - cardH, w - paddingH, h - paddingV);

        float hubX = w / 2f;
        float hubY = h / 2f;
        float hubR = dp(42);

        // 1. Draw Connecting Lines & Animated Particles
        // PV -> Hub
        drawFlowLine(canvas, pvRect.centerX(), pvRect.centerY(), hubX, hubY, colorSolar, solarData.pvPowerW, 1);

        // Hub -> Home
        drawFlowLine(canvas, hubX, hubY, homeRect.centerX(), homeRect.centerY(), colorHouse, solarData.getHouseConsumptionW(), 1);

        // Hub <-> Battery
        int batColor = solarData.isBatteryCharging() ? colorBatteryGreen : colorBatteryCyan;
        int batDir = solarData.batteryPowerW > 10 ? 1 : (solarData.batteryPowerW < -10 ? -1 : 0);
        drawFlowLine(canvas, hubX, hubY, batteryRect.centerX(), batteryRect.centerY(), batColor, solarData.batteryPowerW, batDir);

        // Hub <-> Grid
        int gridColor = solarData.gridPowerW >= 0 ? colorGridPurple : colorGridRed;
        int gridDir = solarData.gridPowerW > 10 ? 1 : (solarData.gridPowerW < -10 ? -1 : 0);
        drawFlowLine(canvas, hubX, hubY, gridRect.centerX(), gridRect.centerY(), gridColor, solarData.gridPowerW, gridDir);

        // 2. Draw Cards
        Context ctx = getContext();
        Locale locale = Locale.getDefault();
        drawNodeCard(canvas, pvRect, ctx.getString(R.string.node_pv_title).toUpperCase(locale), solarData.getFormattedPvPower(),
                solarData.pvPowerW > 10 ? ctx.getString(R.string.node_pv_active) : ctx.getString(R.string.node_pv_inactive), colorSolar, ICON_SUN, null);

        drawNodeCard(canvas, homeRect, ctx.getString(R.string.node_house_title).toUpperCase(locale), solarData.getFormattedHousePower(),
                ctx.getString(R.string.node_house_subtitle), colorHouse, ICON_HOME, null);

        String batSub = !solarData.hasBattery ? ctx.getString(R.string.node_battery_not_present) :
                (solarData.batteryPowerW > 10 ? ctx.getString(R.string.node_battery_charging, solarData.getFormattedBatteryPower()) :
                (solarData.batteryPowerW < -10 ? ctx.getString(R.string.node_battery_discharging, solarData.getFormattedBatteryPower()) : ctx.getString(R.string.node_battery_standby)));
        drawNodeCard(canvas, batteryRect, ctx.getString(R.string.node_battery_title).toUpperCase(locale), solarData.getFormattedBatterySoc(),
                batSub, batColor, ICON_BATTERY, (float) solarData.batterySoc);

        String gridSub = solarData.gridPowerW > 10 ? ctx.getString(R.string.node_grid_export) :
                (solarData.gridPowerW < -10 ? ctx.getString(R.string.node_grid_import) : ctx.getString(R.string.node_grid_balanced));
        drawNodeCard(canvas, gridRect, ctx.getString(R.string.node_grid_title).toUpperCase(locale), solarData.getFormattedGridPower(),
                gridSub, gridColor, ICON_GRID, null);

        // 3. Central Hub
        drawCentralHub(canvas, hubX, hubY, hubR);

        // 4. Update phase for 60 FPS animation
        animPhase = (animPhase + 0.015f) % 1f;
        postInvalidateOnAnimation();
    }

    private void drawFlowLine(Canvas canvas, float x1, float y1, float x2, float y2, int color, int powerW, int direction) {
        boolean isFlowing = direction != 0 && Math.abs(powerW) > 10;

        linePaint.setColor(isFlowing ? adjustAlpha(color, 0.4f) : Color.parseColor("#334155"));
        canvas.drawLine(x1, y1, x2, y2, linePaint);

        if (isFlowing) {
            float dx = x2 - x1;
            float dy = y2 - y1;
            particlePaint.setColor(color);
            particleGlowPaint.setColor(adjustAlpha(color, 0.35f));

            int numParticles = 3;
            for (int i = 0; i < numParticles; i++) {
                float frac = (animPhase + ((float) i / numParticles)) % 1f;
                float effectiveFrac = direction == -1 ? (1f - frac) : frac;

                float px = x1 + dx * effectiveFrac;
                float py = y1 + dy * effectiveFrac;

                canvas.drawCircle(px, py, dp(7), particleGlowPaint);
                canvas.drawCircle(px, py, dp(4), particlePaint);
            }
        }
    }

    private void drawNodeCard(Canvas canvas, RectF rect, String title, String value, String subtitle, int accentColor, int iconType, Float batterySoc) {
        cardPaint.setColor(colorBgCard);
        canvas.drawRoundRect(rect, cardCornerRadius, cardCornerRadius, cardPaint);

        cardBorderPaint.setColor(colorBorderCard);
        canvas.drawRoundRect(rect, cardCornerRadius, cardCornerRadius, cardBorderPaint);

        float pad = dp(16);
        float iconSize = dp(28);
        float iconCx = rect.left + pad + iconSize / 2f;
        float iconCy = rect.top + pad + iconSize / 2f;

        // Circular badge behind icon
        iconFillPaint.setColor(adjustAlpha(accentColor, 0.15f));
        canvas.drawCircle(iconCx, iconCy, iconSize / 2f + dp(4), iconFillPaint);

        drawVectorIcon(canvas, iconCx, iconCy, dp(12), accentColor, iconType);

        textTitlePaint.setColor(colorTextSecondary);
        canvas.drawText(title, rect.left + pad + iconSize + dp(14), iconCy + dp(5), textTitlePaint);

        textValuePaint.setColor(colorTextPrimary);
        float valueY = iconCy + dp(38);
        canvas.drawText(value, rect.left + pad, valueY, textValuePaint);

        float subtitleY = valueY + dp(22);
        if (batterySoc != null) {
            float barY = valueY + dp(10);
            float barW = rect.width() - pad * 2f;
            float barH = dp(6);

            RectF barBg = new RectF(rect.left + pad, barY, rect.left + pad + barW, barY + barH);
            float fillW = barW * Math.min(1f, Math.max(0f, batterySoc / 100f));
            RectF barFill = new RectF(rect.left + pad, barY, rect.left + pad + fillW, barY + barH);

            iconFillPaint.setColor(Color.parseColor("#1E293B"));
            canvas.drawRoundRect(barBg, dp(3), dp(3), iconFillPaint);

            iconFillPaint.setColor(accentColor);
            canvas.drawRoundRect(barFill, dp(3), dp(3), iconFillPaint);

            subtitleY = barY + barH + dp(18);
        }

        textSubtitlePaint.setColor(accentColor);
        canvas.drawText(subtitle, rect.left + pad, subtitleY, textSubtitlePaint);
    }

    private void drawCentralHub(Canvas canvas, float cx, float cy, float r) {
        cardPaint.setColor(Color.parseColor("#0F172A"));
        canvas.drawCircle(cx, cy, r, cardPaint);

        cardBorderPaint.setColor(Color.parseColor("#38BDF8"));
        cardBorderPaint.setStrokeWidth(dp(2));
        canvas.drawCircle(cx, cy, r, cardBorderPaint);

        Paint hubTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
        hubTitle.setColor(colorTextMuted);
        hubTitle.setTextSize(dp(10));
        hubTitle.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        hubTitle.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(getContext().getString(R.string.hub_inverter_ac), cx, cy - dp(6), hubTitle);

        Paint hubVal = new Paint(Paint.ANTI_ALIAS_FLAG);
        hubVal.setColor(colorTextPrimary);
        hubVal.setTextSize(dp(14));
        hubVal.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        hubVal.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(SolarTelemetry.formatWattsOrKw(solarData.activePowerW), cx, cy + dp(12), hubVal);
    }

    private void drawVectorIcon(Canvas canvas, float cx, float cy, float r, int color, int type) {
        iconPaint.setColor(color);
        iconFillPaint.setColor(color);

        switch (type) {
            case ICON_SUN:
                canvas.drawCircle(cx, cy, r * 0.45f, iconFillPaint);
                float rayLen = r * 0.35f;
                for (int i = 0; i < 8; i++) {
                    double rad = Math.toRadians(i * 45);
                    float x1 = (float) (cx + (r * 0.65f * Math.cos(rad)));
                    float y1 = (float) (cy + (r * 0.65f * Math.sin(rad)));
                    float x2 = (float) (cx + ((r * 0.65f + rayLen) * Math.cos(rad)));
                    float y2 = (float) (cy + ((r * 0.65f + rayLen) * Math.sin(rad)));
                    canvas.drawLine(x1, y1, x2, y2, iconPaint);
                }
                break;
            case ICON_HOME:
                Path roof = new Path();
                roof.moveTo(cx, cy - r);
                roof.lineTo(cx + r, cy);
                roof.lineTo(cx - r, cy);
                roof.close();
                canvas.drawPath(roof, iconFillPaint);
                RectF body = new RectF(cx - r * 0.7f, cy, cx + r * 0.7f, cy + r);
                canvas.drawRect(body, iconFillPaint);
                break;
            case ICON_BATTERY:
                RectF bat = new RectF(cx - r * 0.55f, cy - r * 0.8f, cx + r * 0.55f, cy + r * 0.9f);
                canvas.drawRoundRect(bat, dp(2), dp(2), iconPaint);
                RectF tip = new RectF(cx - r * 0.25f, cy - r, cx + r * 0.25f, cy - r * 0.8f);
                canvas.drawRoundRect(tip, dp(1), dp(1), iconFillPaint);
                break;
            case ICON_GRID:
                canvas.drawLine(cx - r * 0.8f, cy - r * 0.4f, cx + r * 0.8f, cy - r * 0.4f, iconPaint);
                canvas.drawLine(cx - r * 0.6f, cy + r * 0.1f, cx + r * 0.6f, cy + r * 0.1f, iconPaint);
                canvas.drawLine(cx, cy - r, cx - r * 0.7f, cy + r, iconPaint);
                canvas.drawLine(cx, cy - r, cx + r * 0.7f, cy + r, iconPaint);
                break;
        }
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.min(255, Math.max(0, Math.round(Color.alpha(color) * factor)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
