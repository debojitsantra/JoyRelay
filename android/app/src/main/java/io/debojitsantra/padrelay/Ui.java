package io.debojitsantra.padrelay;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class C {
    static final int BG = Color.BLACK;
    static final int CARD = 0xff111111;
    static final int CARD_HI = 0xff1b1b1b;
    static final int BORDER = 0xff555555;
    static final int ACCENT = Color.WHITE;
    static final int ACCENT_HI = 0xffdddddd;
    static final int TEXT = Color.WHITE;
    static final int MUTED = 0xffaaaaaa;
    static final int GOOD = Color.WHITE;
    static final int WARN = 0xffcccccc;
    static final int BAD = 0xff888888;

    private C() {}
}

final class Ui {
    private Ui() {}

    static int dp(Context context, float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    static float dpf(Context context, float value) {
        return TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }

    static GradientDrawable squareBg(int fill, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        if (stroke != 0) {
            drawable.setStroke(1, stroke);
        }
        return drawable;
    }

    static LinearLayout card(Context context) {
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(squareBg(C.CARD, C.BORDER));

        int padding = dp(context, 16);
        view.setPadding(padding, padding, padding, padding);
        return view;
    }

    static TextView text(Context context, String value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(
                bold ? "sans-serif-medium" : "sans-serif",
                Typeface.NORMAL
        ));
        return view;
    }

    static TextView caption(Context context, String value) {
        TextView view = text(context, value.toUpperCase(), 11, C.MUTED, true);
        view.setLetterSpacing(.12f);
        return view;
    }

    static Button button(Context context, String label, boolean primary) {
        Button button = new Button(context);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.create("sans-serif-medium", 0));
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(context, 14), 0, dp(context, 14), 0);
        style(button, primary, false);
        return button;
    }

    static void style(Button button, boolean primary, boolean danger) {
        int fill = danger ? C.BAD : primary ? C.ACCENT : C.CARD_HI;
        button.setTextColor(primary || danger ? Color.BLACK : C.TEXT);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(0x44ffffff),
                squareBg(fill, primary || danger ? 0 : C.BORDER),
                null
        ));
    }

    static LinearLayout.LayoutParams lp(Context context, int width, int height, int top) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.topMargin = dp(context, top);
        return params;
    }
}
