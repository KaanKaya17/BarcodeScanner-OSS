package com.example.barcodescanner;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.Locale;

final class Product implements Serializable {
    private static final long serialVersionUID = 1L;

    @SerializedName("code")
    String barcode;

    @SerializedName("product_name")
    String productName;

    @SerializedName("product_name_en")
    String englishName;

    @SerializedName("product_name_tr")
    String turkishName;

    @SerializedName("brands")
    String brands;

    @SerializedName("image_front_small_url")
    String imageUrl;

    @SerializedName("product_quantity_unit")
    String quantityUnit;

    @SerializedName("nutriments")
    Nutriments nutriments;

    @SerializedName("source")
    ProductSource source;

    @SerializedName("last_modified_t")
    Long lastModifiedSeconds;

    @SerializedName("image_nutrition_url")
    String nutritionImageUrl;

    String name(Locale locale, String fallback) {
        String localized = "tr".equals(locale.getLanguage()) ? turkishName : englishName;
        if (hasText(localized)) {
            return localized;
        }
        return hasText(productName) ? productName : fallback;
    }

    static boolean hasText(String text) {
        return text != null && !text.trim().isEmpty();
    }

    boolean usesMilliliters() {
        return "ml".equals(quantityUnit);
    }

    boolean hasKnownBasis() {
        return "g".equals(quantityUnit) || usesMilliliters();
    }

    boolean isPersonal() {
        return source != null && "package_label".equals(source.id);
    }

    boolean canCache() {
        return source == null || (!"fatsecret".equals(source.id)
                && !Boolean.FALSE.equals(source.cacheable));
    }
}
