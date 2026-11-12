package com.example.barcodescanner;

import android.text.InputFilter;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.EnumMap;

final class LabelEditor {
    interface Save {
        void accept(String name, String basis, Nutriments values);
    }

    private LabelEditor() {}

    static void show(AppCompatActivity activity, Product existing, boolean defaultToMl,
            Save save, Runnable delete) {
        LinearLayout fields = new LinearLayout(activity);
        fields.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        fields.setPadding(padding, 0, padding, padding);

        TextView instructions = new TextView(activity);
        instructions.setText(R.string.label_help);
        fields.addView(instructions);

        TextInputLayout name = addField(activity, fields, activity.getString(R.string.label_name),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES, 120);
        if (existing != null) name.getEditText().setText(existing.productName);

        TextView basisLabel = new TextView(activity);
        basisLabel.setText(R.string.label_basis);
        fields.addView(basisLabel);
        Spinner basis = new Spinner(activity);
        basis.setId(Spinner.generateViewId());
        basisLabel.setLabelFor(basis.getId());
        ArrayAdapter<String> choices = new ArrayAdapter<>(activity, android.R.layout.simple_spinner_item,
                new String[]{activity.getString(R.string.label_basis_grams),
                        activity.getString(R.string.label_basis_ml)});
        choices.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        basis.setAdapter(choices);
        basis.setSelection((existing == null ? defaultToMl : existing.usesMilliliters()) ? 1 : 0);
        fields.addView(basis);

        EnumMap<Nutrient, TextInputLayout> inputs = new EnumMap<>(Nutrient.class);
        for (Nutrient nutrient : Nutrient.values()) {
            int hint = nutrient == Nutrient.ENERGY ? R.string.label_field_kcal : R.string.label_field_gram;
            TextInputLayout field = addField(activity, fields,
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

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(fields);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(existing == null ? R.string.label_open : R.string.label_edit)
                .setView(scroll)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.label_delete, (dialog, button) ->
                    new AlertDialog.Builder(activity)
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
                    basis.getSelectedItemPosition() == 0 ? "g" : "ml", values);
            dialog.dismiss();
        }));
        dialog.show();
    }

    static Double parse(String raw) {
        if (!raw.matches("[0-9]+([.,][0-9]+)?")) return null;
        double value = Double.parseDouble(raw.replace(',', '.'));
        return Double.isFinite(value) ? value : null;
    }

    private static TextInputLayout addField(AppCompatActivity activity, LinearLayout parent,
            String hint, int type, int maxLength) {
        TextInputLayout field = new TextInputLayout(activity);
        field.setHint(hint);
        TextInputEditText input = new TextInputEditText(field.getContext());
        input.setSingleLine(true);
        input.setInputType(type);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        field.addView(input);
        parent.addView(field);
        return field;
    }
}
