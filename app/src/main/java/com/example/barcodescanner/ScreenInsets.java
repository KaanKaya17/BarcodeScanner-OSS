package com.example.barcodescanner;

import android.graphics.Color;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

final class ScreenInsets {
    private ScreenInsets() {}

    static void enable(ComponentActivity activity) {
        SystemBarStyle dark = SystemBarStyle.dark(Color.TRANSPARENT);
        EdgeToEdge.enable(activity, dark, dark);
    }

    static void applyTo(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
