package com.example.barcodescanner;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

final class DialogForm {
    private DialogForm() {}

    static LinearLayout content(Context context) {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int inset = dp(context, 24);
        content.setPadding(inset, dp(context, 6), inset, dp(context, 12));
        content.setFocusableInTouchMode(true);
        return content;
    }

    static ScrollView scroll(LinearLayout content) {
        ScrollView scroll = new ScrollView(content.getContext());
        scroll.setFillViewport(true);
        scroll.addView(content);
        return scroll;
    }

    static void addHelp(LinearLayout content, @StringRes int message) {
        TextView help = new TextView(content.getContext());
        help.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        help.setTextColor(content.getContext().getColor(R.color.scanner_muted));
        help.setText(message);
        content.addView(help);
    }

    static TextInputLayout addField(LinearLayout content, CharSequence label, int inputType) {
        Context context = content.getContext();
        TextInputLayout field = new TextInputLayout(context);
        field.setHint(label);
        TextInputEditText input = new TextInputEditText(field.getContext());
        input.setInputType(inputType);
        input.setSingleLine(true);
        field.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, 10);
        content.addView(field, params);
        return field;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
