package com.huaweitv.solar.ui;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.huaweitv.solar.R;
import com.huaweitv.solar.coffee.CoffeePromptManager;

/**
 * TV D-Pad & touch-friendly voluntary coffee prompt dialog (API 19+ pure Java).
 */
public class CoffeePromptDialog extends Dialog {

    public interface Callback {
        void onSnoozed();
        void onConfirmedForever();
    }

    private enum Step {
        PROMPT,
        CONFIRM,
        THANK_YOU
    }

    private final Callback callback;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoDismissRunnable = this::dismiss;

    private Step currentStep = Step.PROMPT;

    private LinearLayout stepPromptLayout;
    private LinearLayout stepConfirmLayout;
    private LinearLayout stepThankYouLayout;

    private Button btnCloseX;
    private TextView tvPayPalLink;
    private Button btnPayPal;
    private Button btnAlready;
    private Button btnConfirmCancel;
    private Button btnConfirmYes;
    private Button btnThankYouOk;

    public CoffeePromptDialog(Context context, Callback callback) {
        super(context);
        this.callback = callback;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_coffee_prompt);
        if (getWindow() != null) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        stepPromptLayout = (LinearLayout) findViewById(R.id.layout_coffee_step_prompt);
        stepConfirmLayout = (LinearLayout) findViewById(R.id.layout_coffee_step_confirm);
        stepThankYouLayout = (LinearLayout) findViewById(R.id.layout_coffee_step_thank_you);

        btnCloseX = (Button) findViewById(R.id.btn_coffee_close_x);
        tvPayPalLink = (TextView) findViewById(R.id.tv_coffee_paypal_link);
        btnPayPal = (Button) findViewById(R.id.btn_coffee_paypal);
        btnAlready = (Button) findViewById(R.id.btn_coffee_already);
        btnConfirmCancel = (Button) findViewById(R.id.btn_coffee_confirm_cancel);
        btnConfirmYes = (Button) findViewById(R.id.btn_coffee_confirm_yes);
        btnThankYouOk = (Button) findViewById(R.id.btn_coffee_thank_you_ok);

        tvPayPalLink.setPaintFlags(tvPayPalLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        setOnCancelListener(dialog -> handleBackOrCancel());

        btnCloseX.setOnClickListener(v -> snoozeAndDismiss());
        tvPayPalLink.setOnClickListener(v -> openPayPalUrl());
        btnPayPal.setOnClickListener(v -> openPayPalUrl());
        btnAlready.setOnClickListener(v -> showStep(Step.CONFIRM));

        btnConfirmCancel.setOnClickListener(v -> showStep(Step.PROMPT));
        btnConfirmYes.setOnClickListener(v -> {
            if (callback != null) {
                callback.onConfirmedForever();
            }
            showStep(Step.THANK_YOU);
        });

        btnThankYouOk.setOnClickListener(v -> dismiss());

        showStep(Step.PROMPT);
    }

    @Override
    public void onBackPressed() {
        handleBackOrCancel();
    }

    private void handleBackOrCancel() {
        if (currentStep == Step.CONFIRM) {
            showStep(Step.PROMPT);
        } else if (currentStep == Step.THANK_YOU) {
            dismiss();
        } else {
            snoozeAndDismiss();
        }
    }

    private void snoozeAndDismiss() {
        if (callback != null) {
            callback.onSnoozed();
        }
        dismiss();
    }

    private void showStep(Step step) {
        currentStep = step;
        handler.removeCallbacks(autoDismissRunnable);

        stepPromptLayout.setVisibility(step == Step.PROMPT ? View.VISIBLE : View.GONE);
        stepConfirmLayout.setVisibility(step == Step.CONFIRM ? View.VISIBLE : View.GONE);
        stepThankYouLayout.setVisibility(step == Step.THANK_YOU ? View.VISIBLE : View.GONE);

        if (step == Step.PROMPT) {
            btnPayPal.requestFocus();
        } else if (step == Step.CONFIRM) {
            btnConfirmCancel.requestFocus();
        } else if (step == Step.THANK_YOU) {
            btnThankYouOk.requestFocus();
            handler.postDelayed(autoDismissRunnable, 3000L);
        }
    }

    private void openPayPalUrl() {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(CoffeePromptManager.PAYPAL_URL));
            getContext().startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
            // Fire TV / Android TV without browser: QR code remains visible for smartphone scan.
        } catch (Exception ignored) {
        }
    }

    @Override
    public void dismiss() {
        handler.removeCallbacks(autoDismissRunnable);
        super.dismiss();
    }
}
