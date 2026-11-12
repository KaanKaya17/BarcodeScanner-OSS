package com.example.barcodescanner;

import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.util.EnumMap;

final class LabelEditor {
    interface Save {
        void accept(String name, String basis, Nutriments values);
    }

    private LabelEditor() {}

    static void show(AppCompatActivity activity, Product existing, boolean defaultToMl,
            Save save, Runnable delete) {
        LinearLayout fields = DialogForm.content(activity);
        DialogForm.addHelp(fields, R.string.label_help);
        TextInputLayout name = addField(fields, activity.getString(R.string.label_name),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 120);
        if (existing != null) name.getEditText().setText(existing.productName);

        TextView basisLabel = new TextView(activity);
        basisLabel.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        basisLabel.setText(R.string.label_basis);
        LinearLayout.LayoutParams basisLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        basisLabelParams.topMargin = Math.round(12 * activity.getResources().getDisplayMetrics().density);
        fields.addView(basisLabel, basisLabelParams);

        String[] units = {activity.getString(R.string.label_basis_grams), activity.getString(R.string.label_basis_ml)};
        MaterialButtonToggleGroup basis = new MaterialButtonToggleGroup(activity);
        basis.setSingleSelection(true);
        basis.setSelectionRequired(true);
        int[] unitIds = new int[units.length];
        for (int index = 0; index < units.length; index++) {
            MaterialButton option = new MaterialButton(activity, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            unitIds[index] = View.generateViewId();
            option.setId(unitIds[index]);
            option.setText(units[index]);
            basis.addView(option, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        fields.addView(basis);
        basis.check(unitIds[(existing == null ? defaultToMl : existing.usesMilliliters()) ? 1 : 0]);

        EnumMap<Nutrient, TextInputLayout> inputs = new EnumMap<>(Nutrient.class);
        for (Nutrient nutrient : Nutrient.values()) {
            int hint = nutrient == Nutrient.ENERGY ? R.string.label_field_kcal : R.string.label_field_gram;
            TextInputLayout field = addField(fields,
                    activity.getString(hint, activity.getString(nutrient.label)),
                    InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, 16);
            if (existing != null && existing.nutriments != null) {
                Double value = nutrient.read.apply(existing.nutriments);
                if (value != null) {
                    String decimal = java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
                    String language = activity.getResources().getConfiguration().getLocales().get(0).getLanguage();
                    field.getEditText().setText("tr".equals(language) ? decimal.replace('.', ',') : decimal);
                }
            }
            inputs.put(nutrient, field);
        }

        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(existing == null ? R.string.label_open : R.string.label_edit)
                .setView(DialogForm.scroll(fields))
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.label_delete, (dialog, button) ->
                    new MaterialAlertDialogBuilder(activity)
                            .setMessage(R.string.label_delete_confirm)
                            .setPositiveButton(R.string.label_delete, (confirm, action) -> delete.run())
                            .setNegativeButton(R.string.cancel, null)
                            .show());
        }

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(unused -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            Nutriments values = new Nutriments();
            boolean hasValue = false;
            for (Nutrient nutrient : Nutrient.values()) {
                TextInputLayout field = inputs.get(nutrient);
                field.setError(null);
                String raw = field.getEditText().getText().toString().trim();
                if (raw.isEmpty()) continue;
                Double value = parse(raw);
                if (value == null) {
                    field.setError(activity.getString(R.string.label_bad_number));
                    field.getEditText().requestFocus();
                    return;
                }
                nutrient.write.accept(values, value);
                hasValue = true;
            }
            if (!hasValue) {
                TextInputLayout field = inputs.get(Nutrient.ENERGY);
                field.setError(activity.getString(R.string.label_empty));
                field.getEditText().requestFocus();
                return;
            }
            save.accept(name.getEditText().getText().toString().trim(),
                    basis.getCheckedButtonId() == unitIds[0] ? "g" : "ml", values);
            dialog.dismiss();
        }));
        dialog.show();
    }

    static Double parse(String raw) {
        if (!raw.matches("[0-9]+([.,][0-9]+)?")) return null;
        double value = Double.parseDouble(raw.replace(',', '.'));
        return Double.isFinite(value) ? value : null;
    }

    private static TextInputLayout addField(LinearLayout parent, String hint, int type, int maxLength) {
        TextInputLayout field = DialogForm.addField(parent, hint, type);
        field.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        return field;
    }
}
