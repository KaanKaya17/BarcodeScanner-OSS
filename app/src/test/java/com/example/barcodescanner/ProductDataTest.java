package com.example.barcodescanner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.gson.Gson;

import org.junit.Test;

import java.util.Locale;

public class ProductDataTest {
    @Test
    public void keepsMissingNullAndZeroNutrientsDistinct() {
        String json = "{\"result\":{\"id\":\"product_found\"},\"product\":{" +
                "\"product_name\":\"Yogurt\",\"product_name_tr\":\"Yoğurt\"," +
                "\"nutriments\":{\"energy-kcal_100g\":71.2,\"fiber_100g\":null,\"sugars_100g\":0}}}";
        FoodResponse response = new Gson().fromJson(json, FoodResponse.class);

        assertEquals("product_found", response.result.id);
        assertEquals("Yoğurt", response.product.name(Locale.forLanguageTag("tr"), "Unknown"));
        assertEquals("Yogurt", response.product.name(Locale.ENGLISH, "Unknown"));
        Nutriments values = response.product.nutriments;
        assertNull(values.saltPer100g);
        assertNull(values.fiberPer100g);
        assertEquals(0.0, values.sugarsPer100g, 0.0);
        assertEquals(178.0, Nutriments.forAmount(values.caloriesPer100g, 250), 0.001);
        assertNull(Nutriments.forAmount(-1.0, 250));
        assertNull(Nutriments.forAmount(Double.NaN, 250));
    }

    @Test
    public void storesNullableValuesWithoutChangingThemToZero() {
        Product product = new Product();
        product.nutriments = new Nutriments();
        product.nutriments.sugarsPer100g = 0.0;
        ScanRecord record = new ScanRecord("96385074", product, 99);
        Gson gson = new Gson();

        ScanRecord restored = gson.fromJson(gson.toJson(record), ScanRecord.class);

        assertEquals(record.barcode, restored.barcode);
        assertEquals(99, restored.savedAt);
        assertNull(restored.product.nutriments.fiberPer100g);
        assertEquals(0.0, restored.product.nutriments.sugarsPer100g, 0.0);
    }

    @Test
    public void separatesMissingProductsFromServiceErrors() {
        assertEquals(ProductRepository.Failure.NOT_FOUND, ProductRepository.httpFailure(404));
        assertEquals(ProductRepository.Failure.RATE_LIMIT, ProductRepository.httpFailure(429));
        assertEquals(ProductRepository.Failure.SERVICE, ProductRepository.httpFailure(503));
    }

    @Test
    public void neverStoresRestrictedProviderDataInHistory() {
        Product licensed = new Product();
        licensed.source = new ProductSource();
        licensed.source.id = "fatsecret";
        Product open = new Product();
        open.productName = "Open record";
        ScanRecord lookup = new ScanRecord("96385074", licensed, 99,
                java.util.Collections.singletonList(open), "TR");

        ScanRecord cached = ProductRepository.cacheableRecord(lookup);

        assertEquals(open, cached.product);
        assertEquals("TR", cached.region);
        assertEquals(0, cached.alternatives.size());
        assertNull(ProductRepository.cacheableRecord(new ScanRecord("96385074", licensed, 99)));
    }

    @Test
    public void readsLocalDecimalInputAndPreservesZero() {
        assertEquals(1.25, LabelEditor.parse("1,25"), 0.0);
        assertEquals(0.0, LabelEditor.parse("0"), 0.0);
        assertNull(LabelEditor.parse("-1"));
        assertNull(LabelEditor.parse("1,000.5"));
    }

    @Test
    public void validatesCatalogAddressesAndNormalizesMarkets() {
        assertEquals("https://example.test/catalog/", CatalogSettings.normalizeUrl("https://example.test/catalog", false));
        assertEquals("", CatalogSettings.normalizeUrl(" ", false));
        assertNull(CatalogSettings.normalizeUrl("http://example.test", true));
        assertNull(CatalogSettings.normalizeUrl("https://name:secret@example.test", false));
        assertNull(CatalogSettings.normalizeUrl("https://example.test?api_key=secret", false));
        assertEquals("http://10.0.2.2:8787/", CatalogSettings.normalizeUrl("http://10.0.2.2:8787", true));
        assertNull(CatalogSettings.normalizeUrl("http://10.0.2.2:8787", false));
        assertEquals("TR", CatalogSettings.normalizeRegion("tr"));
        assertNull(CatalogSettings.normalizeRegion("Turkey"));
    }

    @Test
    public void savedProductSearchAcceptsTurkishCharactersAndBarcodeTerms() {
        String entry = RecordPicker.fold("Çiğ Süt Işığı 8690000000012");
        assertEquals(true, RecordPicker.matches(entry, RecordPicker.fold("cig isigi 8690")));
        assertEquals(false, RecordPicker.matches(entry, RecordPicker.fold("cig 9999")));
    }
}
