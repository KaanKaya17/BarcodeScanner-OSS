package com.example.barcodescanner;

import com.google.gson.annotations.SerializedName;

import java.util.List;

final class FoodResponse {
    @SerializedName("product")
    Product product;

    @SerializedName("barcode")
    String barcode;

    @SerializedName("region")
    String region;

    @SerializedName("alternatives")
    List<Product> alternatives;

    @SerializedName("sources")
    List<ProviderStatus> sources;

    @SerializedName("result")
    Result result;

    static final class Result {
        @SerializedName("id")
        String id;
    }

    static final class ProviderStatus {
        @SerializedName("status")
        String status;
    }

    boolean comparisonIncomplete() {
        if (sources == null) return false;
        for (ProviderStatus source : sources) {
            if (source != null && ("unavailable".equals(source.status) || "rate_limited".equals(source.status))) {
                return true;
            }
        }
        return false;
    }
}
