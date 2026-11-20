package com.videosequence.local;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

abstract class Screen extends Activity {
    static final int BACKGROUND = 0xff101518;
    static final int CARD = 0xff20292d;
    static final int TEXT = 0xfff0f5f4;
    static final int MUTED = 0xffaebdbf;
    static final int MINT = 0xffadeccd;

    int dp(int size) {
        return Math.round(size * getResources().getDisplayMetrics().density);
    }

    LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return layout;
    }

    TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    Button button(String title, Runnable action) {
        Button button = new Button(this, null, android.R.attr.borderlessButtonStyle);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextColor(MINT);
        button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(48));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    void show(LinearLayout root) {
        root.setBackgroundColor(BACKGROUND);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets edges = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(edges.left, edges.top, edges.right, edges.bottom);
            }
            return insets;
        });
        setContentView(root);
    }

    void weighted(LinearLayout parent, View child) {
        parent.addView(child, new LinearLayout.LayoutParams(0, -2, 1));
    }
}
