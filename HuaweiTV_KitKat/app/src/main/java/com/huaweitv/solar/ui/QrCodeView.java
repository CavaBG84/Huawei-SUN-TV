package com.huaweitv.solar.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import com.huaweitv.solar.coffee.CoffeePromptManager;
import com.huaweitv.solar.coffee.QrCodeEncoder;

/**
 * Lightweight custom View rendering a crisp QR Code with white quiet zone on API 19+.
 */
public class QrCodeView extends View {

    private final Paint darkPaint = new Paint();
    private boolean[][] matrix;

    public QrCodeView(Context context) {
        super(context);
        init();
    }

    public QrCodeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public QrCodeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        darkPaint.setColor(Color.BLACK);
        darkPaint.setStyle(Paint.Style.FILL);
        darkPaint.setAntiAlias(false);
        setBackgroundColor(Color.WHITE);
        matrix = QrCodeEncoder.encode(CoffeePromptManager.PAYPAL_URL);
    }

    public void setContent(String text) {
        matrix = QrCodeEncoder.encode(text);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (matrix == null || matrix.length == 0) return;

        int width = getWidth();
        int height = getHeight();
        int minDim = Math.min(width, height);
        int quietPx = Math.max(8, Math.round(minDim * 0.08f));
        float drawSize = Math.max(1f, minDim - 2f * quietPx);

        int count = matrix.length;
        float moduleSize = drawSize / count;
        float startX = (width - drawSize) * 0.5f;
        float startY = (height - drawSize) * 0.5f;

        for (int r = 0; r < count; r++) {
            for (int c = 0; c < count; c++) {
                if (matrix[r][c]) {
                    float left = startX + c * moduleSize;
                    float top = startY + r * moduleSize;
                    canvas.drawRect(left, top, left + moduleSize + 0.5f, top + moduleSize + 0.5f, darkPaint);
                }
            }
        }
    }
}
