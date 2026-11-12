package com.example.barcodescanner;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class FavoriteStore {
    static final class Entry {
        @SerializedName("barcode") final String barcode;
        @SerializedName("added_at") final long addedAt;
        @SerializedName("snapshot") final ScanRecord snapshot;

        Entry(String barcode, long addedAt, ScanRecord snapshot) {
            this.barcode = barcode;
            this.addedAt = addedAt;
            this.snapshot = snapshot;
        }
    }

    private static final String PREFIX = "favorite:";
    private final SharedPreferences storage;
    private final Gson gson = new Gson();

    FavoriteStore(Context context) {
        storage = context.getSharedPreferences("scan_history", Context.MODE_PRIVATE);
    }

    boolean contains(String barcode) {
        return storage.contains(key(barcode));
    }

    void add(String barcode, ScanRecord record) {
        ScanRecord snapshot = record == null ? null : ProductRepository.cacheableRecord(record);
        Entry entry = new Entry(barcode, System.currentTimeMillis(), snapshot);
        storage.edit().putString(key(barcode), gson.toJson(entry)).apply();
    }

    void remove(String barcode) {
        storage.edit().remove(key(barcode)).apply();
    }

    List<Entry> all() {
        List<Entry> entries = new ArrayList<>();
        storage.getAll().forEach((key, value) -> {
            if (!key.startsWith(PREFIX) || !(value instanceof String)) return;
            try {
                Entry entry = gson.fromJson((String) value, Entry.class);
                if (entry != null && BarcodeValue.normalize(entry.barcode) != null) entries.add(entry);
            } catch (JsonParseException ignored) {
                // A damaged favorite does not hide the rest of the list.
            }
        });
        entries.sort(Comparator.comparingLong((Entry entry) -> entry.addedAt).reversed());
        return entries;
    }

    private static String key(String barcode) {
        return PREFIX + BarcodeValue.identifier(barcode);
    }
}
