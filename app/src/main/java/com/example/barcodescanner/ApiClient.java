package com.example.barcodescanner;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

final class ApiClient {
    private static final String BASE_URL = "https://world.openfoodfacts.org/";
    private static final String PROJECT_URL = "https://github.com/KaanKaya17/BarcodeScanner-OSS";

    private ApiClient() {}

    static FoodApiService create(String baseUrl, String version) {
        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(15, TimeUnit.SECONDS)
                .addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                        .header("User-Agent", "BarcodeScanner/" + version + " (" + PROJECT_URL + ")")
                        .build()))
                .build();
        return new Retrofit.Builder()
                .baseUrl(baseUrl.isEmpty() ? BASE_URL : baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FoodApiService.class);
    }
}
