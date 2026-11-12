package com.example.barcodescanner;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class RecordPicker {
    static final class Entry {
        final String display;
        final String search;
        final Runnable open;

        Entry(String display, String search, Runnable open) {
            this.display = display;
            this.search = fold(search);
            this.open = open;
        }
    }

    private RecordPicker() {}

    static void show(AppCompatActivity activity, int title, int emptyText, List<Entry> entries, Runnable clear) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setNegativeButton(R.string.close, null);
        if (entries.isEmpty()) {
            builder.setMessage(emptyText).show();
            return;
        }
        if (clear != null) builder.setNeutralButton(R.string.clear_history, (dialog, button) -> clear.run());

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        int inset = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        content.setPadding(inset, 0, inset, 0);
        content.setFocusableInTouchMode(true);

        EditText search = new EditText(activity);
        search.setId(R.id.recordSearch);
        search.setSingleLine(true);
        search.setHint(R.string.history_search);
        content.addView(search);

        ListView list = new ListView(activity);
        list.setId(R.id.recordList);
        list.setNestedScrollingEnabled(true);
        content.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView empty = new TextView(activity);
        empty.setText(R.string.no_matching_records);
        empty.setPadding(inset, inset, inset, inset);
        content.addView(empty);
        list.setEmptyView(empty);

        List<Entry> visible = new ArrayList<>(entries);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1);
        list.setAdapter(adapter);
        fillAdapter(adapter, visible);

        AlertDialog dialog = builder.setView(content).create();
        list.setOnItemClickListener((parent, view, index, id) -> {
            Entry entry = visible.get(index);
            dialog.dismiss();
            entry.open.run();
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable text) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                visible.clear();
                String query = fold(text.toString()).trim();
                for (Entry entry : entries) {
                    if (matches(entry.search, query)) visible.add(entry);
                }
                fillAdapter(adapter, visible);
            }
        });
        dialog.show();
        content.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
    }

    private static void fillAdapter(ArrayAdapter<String> adapter, List<Entry> entries) {
        adapter.setNotifyOnChange(false);
        adapter.clear();
        for (Entry entry : entries) adapter.add(entry.display);
        adapter.notifyDataSetChanged();
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
