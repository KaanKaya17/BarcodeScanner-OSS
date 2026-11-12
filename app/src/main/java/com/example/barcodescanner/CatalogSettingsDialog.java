package com.example.barcodescanner;

import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

final class CatalogSettingsDialog {
    private CatalogSettingsDialog() {}

    static void show(AppCompatActivity activity, CatalogSettings settings) {
        CatalogSettings.Config config = settings.read();
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, padding);

        TextView help = new TextView(activity);
        help.setText(R.string.catalog_help);
        content.addView(help);

        EditText address = new EditText(activity);
        address.setId(R.id.catalogAddress);
        address.setSingleLine(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setHint(R.string.catalog_address);
        address.setText(config.baseUrl);
        content.addView(address);

        EditText region = new EditText(activity);
        region.setId(R.id.catalogRegion);
        region.setSingleLine(true);
        region.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        region.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        region.setHint(R.string.catalog_region);
        region.setText(config.region);
        content.addView(region);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.catalog_settings)
                .setView(content)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.catalog_reset, (unused, which) -> {
                    settings.restoreDefaults();
                    Toast.makeText(activity, R.string.catalog_saved, Toast.LENGTH_SHORT).show();
                })
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String url = CatalogSettings.normalizeUrl(address.getText().toString(), BuildConfig.DEBUG);
            if (url == null) {
                address.setError(activity.getString(R.string.catalog_invalid_url));
                return;
            }
            String market = CatalogSettings.normalizeRegion(region.getText().toString());
            if (market == null) {
                region.setError(activity.getString(R.string.catalog_invalid_region));
                return;
            }
            settings.save(url, market);
            Toast.makeText(activity, R.string.catalog_saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }
}
