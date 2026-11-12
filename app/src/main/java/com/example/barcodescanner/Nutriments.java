package com.example.barcodescanner;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

final class Nutriments implements Serializable {
    private static final long serialVersionUID = 1L;

    @SerializedName("energy-kcal_100g")
    Double caloriesPer100g;

    @SerializedName("fat_100g")
    Double fatPer100g;

    @SerializedName("proteins_100g")
    Double proteinPer100g;

    @SerializedName("carbohydrates_100g")
    Double carbsPer100g;

    @SerializedName("sugars_100g")
    Double sugarsPer100g;

    @SerializedName("fiber_100g")
    Double fiberPer100g;

    @SerializedName("salt_100g")
    Double saltPer100g;

    static Double forAmount(Double per100, double quantity) {
        if (per100 == null || !Double.isFinite(per100) || per100 < 0) {
            return null;
        }
        double amount = per100 * (quantity / 100);
        return Double.isFinite(amount) ? amount : null;
    }
}
