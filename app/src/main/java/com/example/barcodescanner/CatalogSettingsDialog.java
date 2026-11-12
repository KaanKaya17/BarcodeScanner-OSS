package com.example.barcodescanner;

import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

final class CatalogSettingsDialog {
    private CatalogSettingsDialog() {}

    static void show(AppCompatActivity activity, CatalogSettings settings) {
        CatalogSettings.Config config = settings.read();
        LinearLayout content = DialogForm.content(activity);
        DialogForm.addHelp(content, R.string.catalog_help);

        TextInputLayout addressField = DialogForm.addField(content, activity.getString(R.string.catalog_address),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText address = addressField.getEditText();
        address.setId(R.id.catalogAddress);
        address.setText(config.baseUrl);

        TextInputLayout regionField = DialogForm.addField(content, activity.getString(R.string.catalog_region),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        EditText region = regionField.getEditText();
        region.setId(R.id.catalogRegion);
        region.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        region.setText(config.region);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.catalog_settings)
                .setView(DialogForm.scroll(content))
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.catalog_reset, (unused, which) -> {
                    settings.restoreDefaults();
                    Toast.makeText(activity, R.string.catalog_saved, Toast.LENGTH_SHORT).show();
                })
                .create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            addressField.setError(null);
            regionField.setError(null);
            String url = CatalogSettings.normalizeUrl(address.getText().toString(), BuildConfig.DEBUG);
            if (url == null) {
                addressField.setError(activity.getString(R.string.catalog_invalid_url));
                return;
            }
            String market = CatalogSettings.normalizeRegion(region.getText().toString());
            if (market == null) {
                regionField.setError(activity.getString(R.string.catalog_invalid_region));
                return;
            }
            settings.save(url, market);
            Toast.makeText(activity, R.string.catalog_saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }
}
