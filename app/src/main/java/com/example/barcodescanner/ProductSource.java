package com.example.barcodescanner;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

final class ProductSource implements Serializable {
    private static final long serialVersionUID = 1L;

    @SerializedName("id") String id;
    @SerializedName("name") String name;
    @SerializedName("updated_at") String updatedAt;
    @SerializedName("market") String market;
    @SerializedName("url") String url;
    @SerializedName("label_url") String labelUrl;
    @SerializedName("cacheable") Boolean cacheable;
    @SerializedName("attribution_url") String attributionUrl;
    @SerializedName("terms_url") String termsUrl;
}
