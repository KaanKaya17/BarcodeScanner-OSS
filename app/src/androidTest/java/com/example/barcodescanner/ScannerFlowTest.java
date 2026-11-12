package com.example.barcodescanner;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withHint;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.containsString;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ScannerFlowTest {
    @Test
    public void nutritionShowsMissingDataAndScalesPortion() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Product product = new Product();
        product.productName = "Example product";
        product.quantityUnit = "g";
        product.nutriments = new Nutriments();
        product.nutriments.caloriesPer100g = 200.0;
        product.nutriments.sugarsPer100g = 0.0;
        ScanRecord record = new ScanRecord("96385074", product, 0);

        try (ActivityScenario<NutritionActivity> ignored =
                     ActivityScenario.launch(NutritionActivity.intent(context, record, true))) {
            onView(withId(R.id.tvSaved)).check(matches(isDisplayed()));
            onView(withId(R.id.tvFiber)).check(matches(withText(R.string.no_data)));
            onView(withId(R.id.tvSugar)).check(matches(withText("0 g")));
            onView(withId(R.id.portion)).perform(replaceText("250"));
            onView(withId(R.id.tvCalories)).check(matches(withText("500 kcal")));
        }
    }

    @Test
    public void changesLanguageFromTheScanner() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            try {
                onView(withId(R.id.language)).perform(click());
                onView(withText("Türkçe")).perform(click());
                onView(withId(R.id.manual)).check(matches(withText("Barkod gir")));
                onView(withId(R.id.language)).perform(click());
                onView(withText("English")).perform(click());
                onView(withId(R.id.manual)).check(matches(withText("Enter barcode")));
            } finally {
                scenario.onActivity(activity -> AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList()));
            }
        }
    }

    @Test
    public void comparesCatalogsAndSavesPackageValuesLocally() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        LabelStore store = new LabelStore(context);
        String barcode = "00000000000001";
        store.delete(barcode);

        Product primary = new Product();
        primary.quantityUnit = "g";
        primary.nutriments = new Nutriments();
        primary.nutriments.caloriesPer100g = 100.0;
        Product alternative = new Product();
        alternative.quantityUnit = "g";
        alternative.nutriments = new Nutriments();
        alternative.nutriments.caloriesPer100g = 120.0;
        alternative.source = new ProductSource();
        alternative.source.id = "usda_fdc";
        alternative.source.name = "USDA FoodData Central";

        ScanRecord record = new ScanRecord(barcode, primary, System.currentTimeMillis(),
                java.util.Collections.singletonList(alternative), "US");
        try (ActivityScenario<NutritionActivity> scenario =
                     ActivityScenario.launch(NutritionActivity.intent(context, record, false))) {
            onView(withId(R.id.tvConflict)).check(matches(withText(containsString("USDA FoodData Central"))));
            onView(withId(R.id.btnChooseSource)).perform(scrollTo(), click());
            onView(withText(containsString("USDA FoodData Central ·"))).perform(click());
            onView(withId(R.id.tvCalories)).check(matches(withText("120 kcal")));

            onView(withId(R.id.btnEditLabel)).perform(click());
            onView(withHint(containsString("(kcal)"))).perform(scrollTo(), replaceText("95"));
            onView(withId(android.R.id.button1)).perform(click());
            onView(withId(R.id.tvCalories)).check(matches(withText("95 kcal")));
            org.junit.Assert.assertEquals(95.0, store.find(barcode).product.nutriments.caloriesPer100g, 0.0);
        } finally {
            store.delete(barcode);
        }
    }
}
