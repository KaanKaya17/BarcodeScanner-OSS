package com.example.barcodescanner;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class FinalFeaturesTest {
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

    @Test
    public void readsAnEan13FromALocalPhoto() throws Exception {
        File image = File.createTempFile("barcode-test", ".png", context.getCacheDir());
        Bitmap bitmap = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888);
        try {
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);
            Paint bars = new Paint();
            bars.setColor(Color.BLACK);
            String ean13 = "10100011010011001001000100001010011011000110101010101110011011001101100111001011100101000010101";
            for (int i = 0; i < ean13.length(); i++) {
                if (ean13.charAt(i) == '1') {
                    canvas.drawRect(125 + i * 10, 100, 135 + i * 10, 500, bars);
                }
            }
            try (FileOutputStream out = new FileOutputStream(image)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out));
            }
            CountDownLatch done = new CountDownLatch(1);
            AtomicReference<List<String>> codes = new AtomicReference<>();
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                    GalleryBarcodeReader.read(context, Uri.fromFile(image), result -> {
                        codes.set(result);
                        done.countDown();
                    }));
            assertTrue(done.await(15, TimeUnit.SECONDS));
            assertEquals(Collections.singletonList("3017620422003"), codes.get());
        } finally {
            bitmap.recycle();
            image.delete();
        }
    }

    @Test
    public void favoritesCanBeFilteredAndOpened() {
        String barcode = "00000000000002";
        FavoriteStore store = new FavoriteStore(context);
        store.remove(barcode);
        Product product = new Product();
        product.productName = "Çiğ Süt";
        product.quantityUnit = "g";
        ScanRecord record = new ScanRecord(barcode, product, System.currentTimeMillis());
        try {
            try (ActivityScenario<NutritionActivity> ignored =
                         ActivityScenario.launch(NutritionActivity.intent(context, record, false))) {
                onView(withId(R.id.btnFavorite)).perform(scrollTo(), click());
                onView(withId(R.id.btnFavorite)).check(matches(withText(R.string.favorite_remove)));
            }
            try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
                onView(withId(R.id.favorites)).perform(scrollTo(), click());
                onView(withId(R.id.recordSearch)).perform(replaceText("no match"));
                onView(withText(R.string.no_matching_records)).check(matches(isDisplayed()));
                onView(withId(R.id.recordSearch)).perform(replaceText("cig"), closeSoftKeyboard());
                onView(withText(containsString("Çiğ Süt"))).perform(click());
                onView(withId(R.id.tvProductName)).check(matches(withText("Çiğ Süt")));
            }
        } finally {
            store.remove(barcode);
        }
    }

    @Test
    public void catalogSettingsAreEditableFromTheScanner() {
        CatalogSettings settings = new CatalogSettings(context);
        CatalogSettings.Config original = settings.read();
        try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.catalogSettings)).perform(scrollTo(), click());
            onView(withId(R.id.catalogAddress)).perform(replaceText("https://example.test/api"), closeSoftKeyboard());
            onView(withId(R.id.catalogRegion)).perform(replaceText("us"), closeSoftKeyboard());
            onView(withId(android.R.id.button1)).perform(click());
            assertEquals("https://example.test/api/", settings.read().baseUrl);
            assertEquals("US", settings.read().region);
        } finally {
            settings.save(original.baseUrl, original.region);
        }
    }

    @Test
    public void missingBarcodeIsSavedAndCanBeLookedUpFromHistory() throws Exception {
        String barcode = "00000017";
        CatalogSettings settings = new CatalogSettings(context);
        CatalogSettings.Config original = settings.read();
        SharedPreferences storage = context.getSharedPreferences("scan_history", Context.MODE_PRIVATE);
        String previousHistory = storage.getString("recent_products", null);
        ProductRepository repository = ProductRepository.get(context);
        CountDownLatch retried = new CountDownLatch(1);
        AtomicReference<IOException> fixtureFailure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0, 2, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(10_000);
            settings.save("http://127.0.0.1:" + server.getLocalPort() + "/", "TR");
            Thread fixture = new Thread(() -> {
                try {
                    sendMissing(server, barcode);
                    sendMissing(server, barcode);
                    retried.countDown();
                } catch (IOException error) {
                    fixtureFailure.set(error);
                }
            });
            fixture.setDaemon(true);
            fixture.start();

            AtomicReference<ProductRepository.Result> first = new AtomicReference<>();
            CountDownLatch received = new CountDownLatch(1);
            repository.lookup(barcode, result -> {
                first.set(result);
                received.countDown();
            });
            assertTrue(received.await(10, TimeUnit.SECONDS));
            assertNotNull(first.get());
            assertEquals(ProductRepository.Failure.NOT_FOUND, first.get().failure);

            try (ActivityScenario<MainActivity> ignored = ActivityScenario.launch(MainActivity.class)) {
                onView(withId(R.id.history)).perform(scrollTo(), click());
                onView(withId(R.id.recordSearch)).perform(replaceText(barcode), closeSoftKeyboard());
                onView(allOf(withText(containsString(barcode)), not(withId(R.id.recordSearch)))).perform(click());
                boolean requested = retried.await(10, TimeUnit.SECONDS);
                if (fixtureFailure.get() != null) throw fixtureFailure.get();
                assertTrue("Selecting the history row should repeat the lookup", requested);
            }
        } finally {
            settings.save(original.baseUrl, original.region);
            CountDownLatch idle = new CountDownLatch(1);
            repository.history(records -> idle.countDown());
            assertTrue(idle.await(10, TimeUnit.SECONDS));
            storage.edit().putString("recent_products", previousHistory).apply();
        }
    }

    private static void sendMissing(ServerSocket server, String barcode) throws IOException {
        try (Socket client = server.accept()) {
            client.setSoTimeout(5_000);
            BufferedReader request = new BufferedReader(
                    new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            String start = request.readLine();
            if (start == null || !start.startsWith("GET /v1/products/" + barcode + "?")) {
                throw new IOException("Unexpected catalog request");
            }
            String line;
            while ((line = request.readLine()) != null && !line.isEmpty()) {
                // Read the request before sending the response.
            }
            client.getOutputStream().write(("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        }
    }
}
