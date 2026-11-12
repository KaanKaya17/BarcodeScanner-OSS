package com.example.barcodescanner;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class LabelStore {
    private static final String PREFIX = "label:";
    private final SharedPreferences storage;
    private final Gson gson = new Gson();

    LabelStore(Context context) {
        storage = context.getSharedPreferences("scan_history", Context.MODE_PRIVATE);
    }

    ScanRecord find(String barcode) {
        return read(storage.getString(PREFIX + barcode, null));
    }

    List<ScanRecord> all() {
        List<ScanRecord> labels = new ArrayList<>();
        storage.getAll().forEach((key, value) -> {
            if (key.startsWith(PREFIX) && value instanceof String) {
                ScanRecord record = read((String) value);
                if (record != null) labels.add(record);
            }
        });
        return labels;
    }

    Product save(String barcode, String name, String basis, Nutriments values) {
        long now = System.currentTimeMillis();
        Product product = new Product();
        product.productName = name;
        product.quantityUnit = basis;
        product.nutriments = values;
        product.source = new ProductSource();
        product.source.id = "package_label";
        product.source.updatedAt = Instant.ofEpochMilli(now).toString();
        storage.edit().putString(PREFIX + barcode, gson.toJson(new ScanRecord(barcode, product, now))).apply();
        return product;
    }

    void delete(String barcode) {
        storage.edit().remove(PREFIX + barcode).apply();
    }

    private ScanRecord read(String json) {
        try {
            ScanRecord record = gson.fromJson(json, ScanRecord.class);
            return record != null && record.product != null && record.product.isPersonal() ? record : null;
        } catch (JsonParseException e) {
            return null;
        }
    }
}
