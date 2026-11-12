package com.example.barcodescanner;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import retrofit2.Call;
import retrofit2.Response;

final class ProductRepository {
    enum Failure { NOT_FOUND, CONNECTION, RATE_LIMIT, SERVICE, INVALID_RESPONSE }

    static final class Result {
        final ScanRecord record;
        final boolean cached;
        final Failure failure;

        private Result(ScanRecord record, boolean cached, Failure failure) {
            this.record = record;
            this.cached = cached;
            this.failure = failure;
        }

        static Result found(ScanRecord record, boolean cached) {
            return new Result(record, cached, null);
        }

        static Result failed(Failure failure) {
            return new Result(null, false, failure);
        }
    }

    private static final int HISTORY_LIMIT = 30;
    private static final String HISTORY_KEY = "recent_products";
    private static ProductRepository instance;

    private final SharedPreferences storage;
    private final LabelStore labels;
    private final CatalogSettings settings;
    private FoodApiService service;
    private String serviceUrl;
    private final Gson gson = new Gson();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    static synchronized ProductRepository get(Context context) {
        if (instance == null) {
            Context app = context.getApplicationContext();
            instance = new ProductRepository(
                    app.getSharedPreferences("scan_history", Context.MODE_PRIVATE),
                    new LabelStore(app),
                    new CatalogSettings(app));
        }
        return instance;
    }

    private ProductRepository(SharedPreferences storage, LabelStore labels, CatalogSettings settings) {
        this.storage = storage;
        this.labels = labels;
        this.settings = settings;
    }

    Call<FoodResponse> lookup(String barcode, Consumer<Result> callback) {
        CatalogSettings.Config config = settings.read();
        FoodApiService service = serviceFor(config.baseUrl);
        Call<FoodResponse> call = !config.usesCatalog()
                ? service.getProduct(barcode, FoodApiService.PRODUCT_FIELDS)
                : service.getCatalogProduct(barcode, config.region, true);
        worker.execute(() -> {
            Result result = fetch(barcode, call, config);
            if (!call.isCanceled()) {
                remember(barcode, result);
                main.post(() -> {
                    if (!call.isCanceled()) {
                        callback.accept(result);
                    }
                });
            }
        });
        return call;
    }

    private synchronized FoodApiService serviceFor(String url) {
        if (!url.equals(serviceUrl)) {
            service = ApiClient.create(url, BuildConfig.VERSION_NAME);
            serviceUrl = url;
        }
        return service;
    }

    private Result fetch(String barcode, Call<FoodResponse> call, CatalogSettings.Config config) {
        Response<FoodResponse> response;
        try {
            response = call.execute();
        } catch (IOException e) {
            // Retrofit uses I/O exceptions for malformed or truncated JSON too.
            Failure failure = e instanceof com.google.gson.stream.MalformedJsonException
                    || e instanceof java.io.EOFException
                    ? Failure.INVALID_RESPONSE : Failure.CONNECTION;
            return fromHistory(barcode, failure, config);
        } catch (JsonParseException | IllegalStateException | NumberFormatException e) {
            return fromHistory(barcode, Failure.INVALID_RESPONSE, config);
        }

        if (response.isSuccessful()) {
            FoodResponse body = response.body();
            if (body == null || body.product == null) {
                if (body != null && body.result != null && "product_not_found".equals(body.result.id)) {
                    return fromHistory(barcode, Failure.NOT_FOUND, config);
                }
                return fromHistory(barcode, Failure.INVALID_RESPONSE, config);
            }
            if (!config.usesCatalog()) {
                if (!BarcodeValue.sameCode(barcode, body.product.barcode)) {
                    return fromHistory(barcode, Failure.INVALID_RESPONSE, config);
                }
                setOpenFoodFactsSource(barcode, body.product);
            } else if (!BarcodeValue.sameCode(barcode, body.barcode)
                    || !config.region.equals(body.region) || !identifiedSource(body.product)) {
                return fromHistory(barcode, Failure.INVALID_RESPONSE, config);
            }
            List<Product> alternatives = new ArrayList<>();
            if (config.usesCatalog() && body.alternatives != null) {
                for (Product alternative : body.alternatives) {
                    if (identifiedSource(alternative)) alternatives.add(alternative);
                }
            }
            ScanRecord record = new ScanRecord(barcode, body.product, System.currentTimeMillis(),
                    alternatives, config.region);
            record.catalogUrl = config.baseUrl;
            record.comparisonIncomplete = body.comparisonIncomplete();
            return Result.found(record, false);
        }

        if (response.errorBody() != null) {
            response.errorBody().close();
        }
        return fromHistory(barcode, httpFailure(response.code()), config);
    }

    private static boolean identifiedSource(Product product) {
        return product != null && product.source != null && Product.hasText(product.source.id)
                && !product.isPersonal();
    }

    private static void setOpenFoodFactsSource(String barcode, Product product) {
        ProductSource source = new ProductSource();
        source.id = "open_food_facts";
        source.name = "Open Food Facts";
        source.url = "https://world.openfoodfacts.org/product/" + barcode;
        source.labelUrl = product.nutritionImageUrl;
        Long seconds = product.lastModifiedSeconds;
        if (seconds != null && seconds > 0 && seconds <= 253402300799L) {
            source.updatedAt = Instant.ofEpochSecond(seconds).toString();
        }
        product.source = source;
    }

    static Failure httpFailure(int code) {
        if (code == 404) {
            return Failure.NOT_FOUND;
        }
        if (code == 429) {
            return Failure.RATE_LIMIT;
        }
        return Failure.SERVICE;
    }

    private Result fromHistory(String barcode, Failure failure, CatalogSettings.Config config) {
        if (failure != Failure.NOT_FOUND) {
            for (ScanRecord record : read()) {
                String savedCatalog = record.catalogUrl == null ? "" : record.catalogUrl;
                if (record.product != null && BarcodeValue.sameCode(barcode, record.barcode)
                        && config.baseUrl.equals(savedCatalog)
                        && (!config.usesCatalog() || config.region.equals(record.region))) {
                    return Result.found(record, true);
                }
            }
        }
        ScanRecord personal = labels.find(barcode);
        return personal == null ? Result.failed(failure) : Result.found(personal, true);
    }

    void history(Consumer<List<ScanRecord>> callback) {
        worker.execute(() -> {
            List<ScanRecord> records = read();
            for (ScanRecord label : labels.all()) {
                if (records.stream().noneMatch(record -> record.product != null
                        && BarcodeValue.sameCode(label.barcode, record.barcode))) {
                    records.removeIf(record -> {
                        if (!BarcodeValue.sameCode(label.barcode, record.barcode)) return false;
                        label.lastScannedAt = record.historyDate();
                        return true;
                    });
                    records.add(label);
                }
            }
            records.sort(Comparator.comparingLong(ScanRecord::historyDate).reversed());
            main.post(() -> callback.accept(records));
        });
    }

    void clearHistory(Runnable callback) {
        worker.execute(() -> {
            storage.edit().remove(HISTORY_KEY).apply();
            main.post(callback);
        });
    }

    private List<ScanRecord> read() {
        List<ScanRecord> records = new ArrayList<>();
        try {
            ScanRecord[] stored = gson.fromJson(storage.getString(HISTORY_KEY, null), ScanRecord[].class);
            if (stored != null) {
                for (ScanRecord record : stored) {
                    if (record != null && BarcodeValue.normalize(record.barcode) != null) {
                        records.add(record);
                    }
                }
            }
        } catch (JsonParseException ignored) {
            // The next completed lookup replaces unreadable history.
        }
        return records;
    }

    private void remember(String barcode, Result result) {
        long now = System.currentTimeMillis();
        List<ScanRecord> records = read();
        ScanRecord entry = result.record != null && !result.cached ? cacheableRecord(result.record) : null;
        if (entry == null) {
            // Keep the previous snapshot and its original save date if this lookup cannot be cached.
            entry = records.stream().filter(previous -> BarcodeValue.sameCode(barcode, previous.barcode))
                    .findFirst().orElse(null);
        }
        if (entry == null) entry = new ScanRecord(barcode, null, now);
        entry.lastScannedAt = now;
        records.removeIf(previous -> BarcodeValue.sameCode(barcode, previous.barcode));
        records.add(0, entry);
        if (records.size() > HISTORY_LIMIT) {
            records.subList(HISTORY_LIMIT, records.size()).clear();
        }
        storage.edit().putString(HISTORY_KEY, gson.toJson(records)).apply();
    }

    static ScanRecord cacheableRecord(ScanRecord record) {
        List<Product> products = new ArrayList<>();
        products.add(record.product);
        if (record.alternatives != null) products.addAll(record.alternatives);
        products.removeIf(product -> product == null || !product.canCache());
        if (products.isEmpty()) return null;
        Product primary = products.remove(0);
        ScanRecord cached = new ScanRecord(record.barcode, primary, record.savedAt, products, record.region);
        cached.comparisonIncomplete = record.comparisonIncomplete;
        cached.catalogUrl = record.catalogUrl;
        return cached;
    }
}
