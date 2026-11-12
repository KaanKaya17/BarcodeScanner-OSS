package com.example.barcodescanner;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

final class ScanRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    @SerializedName("barcode")
    final String barcode;

    @SerializedName("product")
    final Product product;

    @SerializedName("saved_at")
    final long savedAt;
    @SerializedName("last_scanned_at")
    long lastScannedAt;

    @SerializedName("alternatives")
    final List<Product> alternatives;

    @SerializedName("region")
    final String region;

    @SerializedName("comparison_incomplete")
    boolean comparisonIncomplete;

    @SerializedName("catalog_url")
    String catalogUrl;

    ScanRecord(String barcode, Product product, long savedAt) {
        this(barcode, product, savedAt, Collections.emptyList(), null);
    }

    ScanRecord(String barcode, Product product, long savedAt, List<Product> alternatives, String region) {
        this.barcode = barcode;
        this.product = product;
        this.savedAt = savedAt;
        this.alternatives = alternatives;
        this.region = region;
    }

    long historyDate() {
        return lastScannedAt > 0 ? lastScannedAt : savedAt;
    }
}
