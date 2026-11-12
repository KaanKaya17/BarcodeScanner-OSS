package com.example.barcodescanner;

import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class RecordPicker {
    static final class Entry {
        final String title;
        final String details;
        final String search;
        final Runnable open;

        Entry(String title, String details, String search, Runnable open) {
            this.title = title;
            this.details = details;
            this.search = fold(search);
            this.open = open;
        }
    }

    private RecordPicker() {}

    static void show(AppCompatActivity activity, int title, int emptyText, List<Entry> entries, Runnable clear) {
        AlertDialog.Builder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(title)
                .setNegativeButton(R.string.close, null);
        if (entries.isEmpty()) {
            builder.setMessage(emptyText).show();
            return;
        }
        if (clear != null) builder.setNeutralButton(R.string.clear_history, (dialog, button) -> clear.run());

        LinearLayout content = DialogForm.content(activity);
        TextInputLayout searchField = DialogForm.addField(content, activity.getString(R.string.history_search),
                InputType.TYPE_CLASS_TEXT);
        searchField.setStartIconDrawable(R.drawable.ic_search);
        searchField.setEndIconMode(TextInputLayout.END_ICON_CLEAR_TEXT);
        EditText search = searchField.getEditText();
        search.setId(R.id.recordSearch);

        ListView list = new ListView(activity);
        list.setId(R.id.recordList);
        list.setNestedScrollingEnabled(true);
        list.setDivider(new ColorDrawable(activity.getColor(R.color.scanner_line)));
        list.setDividerHeight(Math.max(1, Math.round(activity.getResources().getDisplayMetrics().density)));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        listParams.topMargin = Math.round(8 * activity.getResources().getDisplayMetrics().density);
        content.addView(list, listParams);

        TextView empty = new TextView(activity);
        empty.setText(R.string.no_matching_records);
        int inset = Math.round(12 * activity.getResources().getDisplayMetrics().density);
        empty.setPadding(inset, inset, inset, inset);
        content.addView(empty);
        list.setEmptyView(empty);

        ArrayAdapter<Entry> adapter = new ArrayAdapter<Entry>(activity, R.layout.record_row, new ArrayList<>(entries)) {
            @NonNull
            @Override
            public View getView(int position, View recycled, @NonNull ViewGroup parent) {
                View row = recycled == null
                        ? LayoutInflater.from(getContext()).inflate(R.layout.record_row, parent, false) : recycled;
                Entry entry = getItem(position);
                ((TextView) row.findViewById(R.id.recordTitle)).setText(entry.title);
                ((TextView) row.findViewById(R.id.recordDetails)).setText(entry.details);
                return row;
            }
        };
        list.setAdapter(adapter);

        AlertDialog dialog = builder.setView(content).create();
        list.setOnItemClickListener((parent, view, index, id) -> {
            Entry entry = adapter.getItem(index);
            dialog.dismiss();
            entry.open.run();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable text) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                adapter.setNotifyOnChange(false);
                adapter.clear();
                String query = fold(text.toString()).trim();
                for (Entry entry : entries) {
                    if (matches(entry.search, query)) adapter.add(entry);
                }
                adapter.notifyDataSetChanged();
            }
        });
        dialog.show();
        content.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    static boolean matches(String normalizedText, String normalizedQuery) {
        for (String word : normalizedQuery.split("\\s+")) {
            if (!normalizedText.contains(word)) return false;
        }
        return true;
    }

    static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replace('ı', 'i');
    }
}
