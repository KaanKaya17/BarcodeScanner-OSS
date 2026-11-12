package com.example.barcodescanner;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.IntentCompat;
import androidx.core.view.ViewCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class NutritionActivity extends AppCompatActivity {
    private static final String RECORD = "record";
    private static final String CACHED = "cached";
    private static final String OPEN_LABEL = "open_label";
    private static final String SELECTED_SOURCE = "selected_source";

    private final List<Product> choices = new ArrayList<>();
    private Locale locale;
    private ScanRecord record;
    private Product selected;
    private LabelStore labels;
    private FavoriteStore favorites;
    private EditText portion;
    private TextInputLayout portionField;
    private boolean cached;
    private boolean emptyCatalog;

    static Intent intent(Context context, ScanRecord record, boolean cached) {
        return new Intent(context, NutritionActivity.class).putExtra(RECORD, record).putExtra(CACHED, cached);
    }

    static Intent labelIntent(Context context, String barcode) {
        ScanRecord empty = new ScanRecord(barcode, new Product(), System.currentTimeMillis());
        return intent(context, empty, false).putExtra(OPEN_LABEL, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        record = IntentCompat.getSerializableExtra(getIntent(), RECORD, ScanRecord.class);
        if (record == null || record.product == null) {
            finish();
            return;
        }

        ScreenInsets.enable(this);
        setContentView(R.layout.activity_nutrition);
        findViewById(R.id.productPhoto).setClipToOutline(true);
        ScreenInsets.applyTo(findViewById(R.id.nutritionRoot));
        ViewCompat.setAccessibilityHeading(findViewById(R.id.tvProductName), true);
        ViewCompat.setAccessibilityHeading(findViewById(R.id.tvNutritionHeading), true);
        locale = getResources().getConfiguration().getLocales().get(0);
        labels = new LabelStore(this);
        favorites = new FavoriteStore(this);
        cached = getIntent().getBooleanExtra(CACHED, false);
        emptyCatalog = getIntent().getBooleanExtra(OPEN_LABEL, false);

        text(R.id.tvBarcode, getString(R.string.barcode_label, record.barcode));
        if (cached && !record.product.isPersonal()) {
            text(R.id.tvSaved, getString(R.string.saved_product, formatDate(record.savedAt)));
            findViewById(R.id.tvSaved).setVisibility(View.VISIBLE);
        }

        portionField = findViewById(R.id.portionField);
        portion = findViewById(R.id.portion);
        resetPortion();
        portion.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) {
                showNutrition(text.toString());
            }
            @Override public void afterTextChanged(Editable text) {}
        });

        findViewById(R.id.btnRescan).setOnClickListener(view -> finish());
        findViewById(R.id.btnChooseSource).setOnClickListener(view -> showSourcePicker());
        findViewById(R.id.btnEditLabel).setOnClickListener(view -> editLabel());
        findViewById(R.id.btnFavorite).setOnClickListener(view -> toggleFavorite());
        findViewById(R.id.btnShare).setOnClickListener(view -> shareProduct());
        updateFavoriteButton();
        refreshChoices(savedInstanceState == null ? null : savedInstanceState.getString(SELECTED_SOURCE));
        if (savedInstanceState == null && emptyCatalog) editLabel();
    }

    private void toggleFavorite() {
        if (favorites.contains(record.barcode)) {
            favorites.remove(record.barcode);
        } else {
            ScanRecord snapshot = emptyCatalog || record.product.isPersonal()
                    ? labels.find(record.barcode) : record;
            favorites.add(record.barcode, snapshot);
        }
        updateFavoriteButton();
    }

    private void updateFavoriteButton() {
        ((TextView) findViewById(R.id.btnFavorite)).setText(favorites.contains(record.barcode)
                ? R.string.favorite_remove : R.string.favorite_add);
    }

    private void shareProduct() {
        StringBuilder text = new StringBuilder();
        Product product = selected;
        if (product != null && product.canCache()) {
            String name = product.name(locale, "");
            if (Product.hasText(name)) text.append(name).append('\n');
        }
        text.append(getString(R.string.barcode_label, record.barcode));
        String url = productSourceUrl(product);
        if (safeHttps(url) == null) {
            for (Product alternative : choices) {
                String candidate = productSourceUrl(alternative);
                if (safeHttps(candidate) != null) {
                    url = candidate;
                    break;
                }
            }
        }
        if (safeHttps(url) != null) text.append('\n').append(url);
        Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text.toString());
        startActivity(Intent.createChooser(intent, getString(R.string.share_product)));
    }

    private String productSourceUrl(Product product) {
        if (product == null || product.isPersonal()) return null;
        return product.source == null
                ? "https://world.openfoodfacts.org/product/" + record.barcode : product.source.url;
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (selected != null) state.putString(SELECTED_SOURCE, sourceId(selected));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (selected != null && !selected.canCache() && !usable(selected)) refreshChoices(null);
    }

    private void refreshChoices(String preferred) {
        choices.clear();
        ScanRecord personal = labels.find(record.barcode);
        if (personal != null) choices.add(personal.product);
        if (!emptyCatalog && usable(record.product)) choices.add(record.product);
        if (record.alternatives != null) {
            for (Product product : record.alternatives) {
                if (usable(product)) choices.add(product);
            }
        }

        Product next = choices.stream().filter(product -> sourceId(product).equals(preferred))
                .findFirst().orElse(choices.isEmpty() ? null : choices.get(0));
        select(next);
        findViewById(R.id.btnChooseSource).setVisibility(choices.size() > 1 ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.btnEditLabel)).setText(personal == null ? R.string.label_open : R.string.label_edit);
    }

    private boolean usable(Product product) {
        return product != null && !product.isPersonal() && (product.canCache()
                || System.currentTimeMillis() - record.savedAt < TimeUnit.HOURS.toMillis(24));
    }

    private void select(Product product) {
        Product previous = selected;
        selected = product;
        if (previous != null && product != null && previous.usesMilliliters() != product.usesMilliliters()) {
            resetPortion();
        }

        String fallback = getString(R.string.unknown_product);
        if (product != null && product.isPersonal()) {
            for (Product catalog : choices) {
                if (!catalog.isPersonal()) {
                    fallback = catalog.name(locale, fallback);
                    break;
                }
            }
        }
        text(R.id.tvProductName, product == null ? fallback : product.name(locale, fallback));
        optionalText(R.id.tvBrand, product == null ? null : product.brands);
        loadPhoto(product == null ? null : product.imageUrl);
        showSource(product);
        showNutrition(portion.getText().toString());
    }

    private void showSource(Product product) {
        ProductSource source = product == null ? null : product.source;
        String label = product == null ? getString(R.string.no_label_source)
                : getString(R.string.source_heading, sourceName(product));
        if (source != null) {
            String date = formattedTimestamp(source.updatedAt);
            label += "\n" + (date == null ? getString(R.string.source_date_missing)
                    : getString(R.string.source_updated, date));
            if (source.market != null && source.market.matches("[A-Z]{2}")) {
                String market = new Locale.Builder().setRegion(source.market).build().getDisplayCountry(locale);
                label += "\n" + getString(R.string.source_market, market);
            }
        }
        text(R.id.tvSource, label);
        setLink(R.id.btnSource, productSourceUrl(product));
        setLink(R.id.btnLabelPhoto, source == null ? null : source.labelUrl);
        setLink(R.id.btnAttribution, source == null ? null : source.attributionUrl);
        setLink(R.id.btnProviderTerms, source == null ? null : source.termsUrl);

        List<String> warnings = new ArrayList<>();
        if (record.comparisonIncomplete) warnings.add(getString(R.string.source_comparison_incomplete));
        if (product != null && product.hasKnownBasis() && hasValues(product.nutriments)) {
            for (Product other : choices) {
                if (other == product || !other.hasKnownBasis() || !hasValues(other.nutriments)) continue;
                if (other.usesMilliliters() != product.usesMilliliters()) {
                    warnings.add(getString(R.string.source_basis_difference, sourceName(other)));
                    continue;
                }
                List<String> fields = new ArrayList<>();
                for (Nutrient nutrient : Nutrient.values()) {
                    Double a = nutrient.read.apply(product.nutriments);
                    Double b = nutrient.read.apply(other.nutriments);
                    if (a != null && b != null && Math.abs(a - b) > nutrient.tolerance + 1e-9) {
                        fields.add(getString(R.string.source_difference_field, getString(nutrient.label),
                                formatValue(nutrient, a), formatValue(nutrient, b)));
                    }
                }
                if (!fields.isEmpty()) {
                    warnings.add(getString(R.string.source_difference, sourceName(other), String.join(", ", fields)));
                }
            }
        }
        optionalText(R.id.tvConflict, String.join("\n", warnings));
    }

    private void showSourcePicker() {
        List<Product> available = new ArrayList<>();
        for (Product product : choices) {
            if (product.isPersonal() || usable(product)) available.add(product);
        }
        if (available.size() < 2) {
            refreshChoices(null);
            return;
        }

        String[] items = available.stream().map(product -> getString(R.string.select_source_option,
                sourceName(product), getString(product.usesMilliliters() ? R.string.label_basis_ml
                        : product.hasKnownBasis() ? R.string.label_basis_grams : R.string.nutrition_basis_unknown)))
                .toArray(String[]::new);
        new MaterialAlertDialogBuilder(this).setTitle(R.string.select_source)
                .setSingleChoiceItems(items, available.indexOf(selected), (dialog, position) -> {
                    select(available.get(position));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void editLabel() {
        ScanRecord local = labels.find(record.barcode);
        LabelEditor.show(this, local == null ? null : local.product,
                selected != null && selected.usesMilliliters(), (name, basis, values) -> {
                    labels.save(record.barcode, name, basis, values);
                    refreshChoices("package_label");
                    Toast.makeText(this, R.string.label_saved, Toast.LENGTH_SHORT).show();
                }, () -> {
                    labels.delete(record.barcode);
                    refreshChoices(null);
                });
    }

    private void showNutrition(String entered) {
        Product product = selected;
        boolean knownBasis = product != null && product.hasKnownBasis();
        portionField.setVisibility(knownBasis ? View.VISIBLE : View.GONE);

        int quantity = 100;
        if (knownBasis && !entered.isEmpty()) {
            try {
                quantity = Integer.parseInt(entered);
            } catch (NumberFormatException e) {
                quantity = 0;
            }
        }
        boolean valid = quantity > 0 && quantity <= 10_000;
        if (knownBasis) {
            boolean volume = product.usesMilliliters();
            portionField.setHint(getString(volume ? R.string.portion_label_ml : R.string.portion_label));
            portionField.setError(valid ? null : getString(volume ? R.string.portion_error_ml : R.string.portion_error));
            int heading = volume ? R.string.nutrition_per_volume : R.string.nutrition_per_amount;
            text(R.id.tvNutritionHeading, valid
                    ? getString(heading, NumberFormat.getIntegerInstance(locale).format(quantity))
                    : getString(R.string.nutrition_heading));
        } else {
            int heading = product != null && hasValues(product.nutriments)
                    ? R.string.nutrition_basis_unknown : R.string.nutrition_heading;
            text(R.id.tvNutritionHeading, getString(heading));
        }

        for (Nutrient nutrient : Nutrient.values()) {
            Double value = product == null || product.nutriments == null || !valid
                    ? null : Nutriments.forAmount(nutrient.read.apply(product.nutriments), quantity);
            text(nutrient.view, value == null ? getString(R.string.no_data) : formatValue(nutrient, value));
        }
    }

    private String formatValue(Nutrient nutrient, double value) {
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMaximumFractionDigits(nutrient == Nutrient.ENERGY ? 1 : 2);
        return getString(nutrient.unit, format.format(value));
    }

    private boolean hasValues(Nutriments values) {
        if (values == null) return false;
        for (Nutrient nutrient : Nutrient.values()) {
            if (Nutriments.forAmount(nutrient.read.apply(values), 100) != null) return true;
        }
        return false;
    }

    private String sourceName(Product product) {
        if (product.isPersonal()) return getString(R.string.package_label);
        return product.source != null && Product.hasText(product.source.name) ? product.source.name : "Open Food Facts";
    }

    private String sourceId(Product product) {
        return product.source != null && Product.hasText(product.source.id) ? product.source.id : "open_food_facts";
    }

    private void resetPortion() {
        portion.setText(NumberFormat.getIntegerInstance(locale).format(100));
    }

    private String formattedTimestamp(String timestamp) {
        try {
            return timestamp == null ? null : formatDate(Instant.parse(timestamp).toEpochMilli());
        } catch (DateTimeParseException | ArithmeticException e) {
            return null;
        }
    }

    private String formatDate(long milliseconds) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
                .format(new Date(milliseconds));
    }

    private void setLink(int viewId, String url) {
        View button = findViewById(viewId);
        Uri uri = safeHttps(url);
        boolean visible = uri != null;
        button.setVisibility(visible ? View.VISIBLE : View.GONE);
        button.setOnClickListener(visible ? view -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.no_browser, Toast.LENGTH_SHORT).show();
            }
        } : null);
    }

    private static Uri safeHttps(String url) {
        Uri uri = url == null ? null : Uri.parse(url);
        return uri != null && "https".equals(uri.getScheme())
                && uri.getHost() != null && uri.getUserInfo() == null ? uri : null;
    }

    private void loadPhoto(String url) {
        ImageView photo = findViewById(R.id.productPhoto);
        Glide.with(this).clear(photo);
        photo.setVisibility(View.GONE);
        if (!Product.hasText(url)) return;
        Uri uri = Uri.parse(url);
        if (!"https".equals(uri.getScheme()) || (!"images.openfoodfacts.org".equals(uri.getHost())
                && !"static.openfoodfacts.org".equals(uri.getHost()))) return;

        Glide.with(this).load(url).onlyRetrieveFromCache(cached).override(600, 400)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(GlideException error, Object model,
                            Target<Drawable> target, boolean first) {
                        photo.setVisibility(View.GONE);
                        return false;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                            Target<Drawable> target, DataSource source, boolean first) {
                        photo.setVisibility(View.VISIBLE);
                        return false;
                    }
                }).into(photo);
    }

    private void optionalText(int viewId, String value) {
        TextView view = findViewById(viewId);
        view.setText(value);
        view.setVisibility(Product.hasText(value) ? View.VISIBLE : View.GONE);
    }

    private void text(int viewId, String value) {
        TextView view = findViewById(viewId);
        view.setText(value);
    }
}
