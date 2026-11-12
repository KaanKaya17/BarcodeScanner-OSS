package com.example.barcodescanner;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

interface FoodApiService {
    String PRODUCT_FIELDS = "code,product_name,product_name_en,product_name_tr,brands,"
            + "image_front_small_url,image_nutrition_url,product_quantity_unit,last_modified_t,nutriments";

    @GET("api/v3/product/{barcode}")
    Call<FoodResponse> getProduct(@Path("barcode") String barcode, @Query("fields") String fields);

    @GET("v1/products/{barcode}")
    Call<FoodResponse> getCatalogProduct(@Path("barcode") String barcode,
            @Query("region") String region, @Query("compare") boolean compare);
}
