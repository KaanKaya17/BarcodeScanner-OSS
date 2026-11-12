package com.example.barcodescanner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraState;
import androidx.camera.core.TorchState;
import androidx.camera.view.CameraController;
import androidx.camera.view.LifecycleCameraController;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String CAMERA_PROMPTED = "camera_prompted";
    private final Rect frameBounds = new Rect();
    private final ActivityResultLauncher<String> permission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startCamera();
                } else {
                    updateCameraStatus(R.string.camera_permission_required, true);
                }
            });
    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) this.model.scanPhoto(uri);
            });

    private ScannerViewModel model;
    private CatalogSettings catalogSettings;
    private FavoriteStore favorites;
    private LabelStore labels;
    private PreviewView preview;
    private View frame;
    private View cameraPanel;
    private TextView cameraStatus;
    private TextView scanStatus;
    private View progress;
    private Button cameraAction;
    private Button retry;
    private Button reset;
    private Button torch;
    private Button manual;
    private Button history;
    private Button language;
    private Button gallery;
    private Button favoriteList;
    private Button catalog;
    private LifecycleCameraController controller;
    private CameraInfo observedCameraInfo;
    private BarcodeAnalyzer analyzer;
    private ExecutorService analysisExecutor;
    private boolean foreground;
    private boolean historyRequested;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScreenInsets.enable(this);
        setContentView(R.layout.activity_main);
        ScreenInsets.applyTo(findViewById(R.id.main));
        ViewCompat.setAccessibilityHeading(findViewById(R.id.scannerHeading), true);

        preview = findViewById(R.id.previewView);
        frame = findViewById(R.id.scanFrame);
        cameraPanel = findViewById(R.id.cameraPanel);
        cameraStatus = findViewById(R.id.cameraStatus);
        scanStatus = findViewById(R.id.scanStatus);
        progress = findViewById(R.id.lookupProgress);
        cameraAction = findViewById(R.id.cameraAction);
        retry = findViewById(R.id.retry);
        reset = findViewById(R.id.reset);
        torch = findViewById(R.id.torch);
        manual = findViewById(R.id.manual);
        history = findViewById(R.id.history);
        language = findViewById(R.id.language);
        gallery = findViewById(R.id.gallery);
        favoriteList = findViewById(R.id.favorites);
        catalog = findViewById(R.id.catalogSettings);
        catalogSettings = new CatalogSettings(this);
        favorites = new FavoriteStore(this);
        labels = new LabelStore(this);

        preview.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int width = Math.min(dp(320), (right - left) * 4 / 5);
            int height = Math.min(dp(180), (bottom - top) * 3 / 5);
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) frame.getLayoutParams();
            if (params.width != width || params.height != height) {
                params.width = width;
                params.height = height;
                frame.setLayoutParams(params);
            }
        });

        model = new ViewModelProvider(this).get(ScannerViewModel.class);
        model.state().observe(this, this::render);
        model.history().observe(this, records -> {
            if (historyRequested) {
                historyRequested = false;
                if (foreground) {
                    showHistory(records);
                }
            }
        });
        cameraAction.setOnClickListener(view -> requestCamera());
        torch.setOnClickListener(view -> toggleTorch());
        retry.setOnClickListener(view -> model.retry());
        reset.setOnClickListener(view -> model.reset());
        findViewById(R.id.addMissingLabel).setOnClickListener(view -> {
            ScannerViewModel.State state = model.state().getValue();
            if (state.phase == ScannerViewModel.Phase.ERROR) {
                model.reset();
                startActivity(NutritionActivity.labelIntent(this, state.barcode));
            }
        });
        manual.setOnClickListener(view -> showManualEntry());
        history.setOnClickListener(view -> {
            historyRequested = true;
            model.loadHistory();
        });
        language.setOnClickListener(view -> showLanguages());
        gallery.setOnClickListener(view -> photoPicker.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE).build()));
        favoriteList.setOnClickListener(view -> showFavorites());
        catalog.setOnClickListener(view -> CatalogSettingsDialog.show(this, catalogSettings));
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        foreground = true;
        if (hasCameraPermission()) {
            startCamera();
        } else {
            releaseCamera();
            updateCameraStatus(R.string.camera_permission_required, true);
        }
        render(model.state().getValue());
    }

    @Override
    protected void onPause() {
        foreground = false;
        if (controller != null && observedCameraInfo != null && observedCameraInfo.hasFlashUnit()) {
            controller.enableTorch(false);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        releaseCamera();
        super.onDestroy();
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCamera() {
        if (hasCameraPermission()) {
            releaseCamera();
            startCamera();
            return;
        }
        if (getPreferences(MODE_PRIVATE).getBoolean(CAMERA_PROMPTED, false)
                && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            Intent settings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
            startActivity(settings);
            return;
        }
        getPreferences(MODE_PRIVATE).edit().putBoolean(CAMERA_PROMPTED, true).apply();
        permission.launch(Manifest.permission.CAMERA);
    }

    private void startCamera() {
        if (controller != null || !hasCameraPermission() || isFinishing()) {
            return;
        }
        PackageManager features = getPackageManager();
        if (!features.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            updateCameraStatus(R.string.camera_unavailable, false);
            return;
        }

        updateCameraStatus(R.string.camera_starting, false);
        LifecycleCameraController camera = new LifecycleCameraController(this);
        controller = camera;
        analysisExecutor = Executors.newSingleThreadExecutor();
        analyzer = new BarcodeAnalyzer();
        try {
            camera.setCameraSelector(features.hasSystemFeature(PackageManager.FEATURE_CAMERA)
                    ? CameraSelector.DEFAULT_BACK_CAMERA : CameraSelector.DEFAULT_FRONT_CAMERA);
            camera.setEnabledUseCases(CameraController.IMAGE_ANALYSIS);
            camera.setImageAnalysisAnalyzer(analysisExecutor,
                    analyzer.create(ContextCompat.getMainExecutor(this), barcodes -> {
                        if (controller != camera || !foreground || !hasWindowFocus() || !frame.isShown()) {
                            return;
                        }
                        frame.getHitRect(frameBounds);
                        model.onFrame(BarcodeAnalyzer.closestBarcode(barcodes, frameBounds), SystemClock.elapsedRealtime());
                    }));
            preview.setController(camera);
            camera.bindToLifecycle(this);
        } catch (RuntimeException e) {
            cameraFailed();
            return;
        }

        camera.getInitializationFuture().addListener(() -> {
            if (controller != camera) {
                return;
            }
            try {
                camera.getInitializationFuture().get();
                CameraInfo info = camera.getCameraInfo();
                if (info == null) {
                    cameraFailed();
                    return;
                }
                observedCameraInfo = info;
                torch.setVisibility(info.hasFlashUnit() ? View.VISIBLE : View.GONE);
                info.getTorchState().observe(this, state ->
                        torch.setText(state != null && state == TorchState.ON ? R.string.torch_off : R.string.torch_on));
                info.getCameraState().observe(this, state -> {
                    if (state.getError() != null) {
                        updateCameraStatus(R.string.camera_error, true);
                    } else if (state.getType() == CameraState.Type.OPEN) {
                        showCamera();
                    } else {
                        updateCameraStatus(R.string.camera_starting, false);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                cameraFailed();
            } catch (java.util.concurrent.ExecutionException | RuntimeException e) {
                cameraFailed();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleTorch() {
        if (controller == null || observedCameraInfo == null || !observedCameraInfo.hasFlashUnit()) {
            return;
        }
        Integer state = observedCameraInfo.getTorchState().getValue();
        controller.enableTorch(state == null || state != TorchState.ON);
    }

    private void cameraFailed() {
        releaseCamera();
        updateCameraStatus(R.string.camera_error, true);
    }

    private void releaseCamera() {
        if (controller == null) {
            return;
        }
        LifecycleCameraController camera = controller;
        controller = null;
        if (observedCameraInfo != null) {
            observedCameraInfo.getCameraState().removeObservers(this);
            observedCameraInfo.getTorchState().removeObservers(this);
            observedCameraInfo = null;
        }
        camera.clearImageAnalysisAnalyzer();
        preview.setController(null);
        camera.unbind();
        analyzer.close();
        analysisExecutor.shutdown();
        analyzer = null;
        analysisExecutor = null;
    }

    private void updateCameraStatus(int message, boolean allowAction) {
        frame.setVisibility(View.INVISIBLE);
        updateScanStatusVisibility();
        torch.setVisibility(View.GONE);
        cameraPanel.setVisibility(View.VISIBLE);
        cameraStatus.setText(message);
        cameraAction.setVisibility(allowAction ? View.VISIBLE : View.GONE);
        if (allowAction) {
            int action = R.string.allow_camera;
            if (hasCameraPermission()) {
                action = R.string.retry_camera;
            } else if (getPreferences(MODE_PRIVATE).getBoolean(CAMERA_PROMPTED, false)
                    && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                action = R.string.open_settings;
            }
            cameraAction.setText(action);
        }
    }

    private void showCamera() {
        cameraPanel.setVisibility(View.GONE);
        frame.setVisibility(View.VISIBLE);
        updateScanStatusVisibility();
        torch.setVisibility(observedCameraInfo.hasFlashUnit() ? View.VISIBLE : View.GONE);
    }

    private void render(ScannerViewModel.State state) {
        boolean loading = state.phase == ScannerViewModel.Phase.LOADING;
        boolean readingPhoto = state.phase == ScannerViewModel.Phase.READING_PHOTO;
        boolean busy = loading || readingPhoto || state.phase == ScannerViewModel.Phase.PHOTO_RESULT;
        boolean error = state.phase == ScannerViewModel.Phase.ERROR;
        progress.setVisibility(loading || readingPhoto ? View.VISIBLE : View.GONE);
        retry.setVisibility(error && state.result.failure != ProductRepository.Failure.NOT_FOUND
                ? View.VISIBLE : View.GONE);
        reset.setVisibility(busy || error ? View.VISIBLE : View.GONE);
        findViewById(R.id.addMissingLabel).setVisibility(error ? View.VISIBLE : View.GONE);
        reset.setText(busy ? R.string.cancel : R.string.btn_rescan);
        manual.setEnabled(!busy);
        history.setEnabled(!busy);
        language.setEnabled(!busy);
        gallery.setEnabled(!busy);
        favoriteList.setEnabled(!busy);
        catalog.setEnabled(!busy);
        if (loading) {
            scanStatus.setText(getString(R.string.loading_product, state.barcode));
        } else if (readingPhoto) {
            scanStatus.setText(R.string.photo_reading);
        } else if (error) {
            scanStatus.setText(errorMessage(state.result.failure));
        } else {
            scanStatus.setText(R.string.scan_hint);
        }
        updateScanStatusVisibility();

        if (state.phase == ScannerViewModel.Phase.PHOTO_RESULT && foreground) {
            model.reset();
            showPhotoResult(state.photoCodes);
        } else if (state.phase == ScannerViewModel.Phase.RESULT && foreground) {
            foreground = false;
            model.reset();
            startActivity(NutritionActivity.intent(this, state.result.record, state.result.cached));
        }
    }

    private void updateScanStatusVisibility() {
        ScannerViewModel.Phase phase = model.state().getValue().phase;
        boolean show = frame.getVisibility() == View.VISIBLE
                || phase == ScannerViewModel.Phase.LOADING || phase == ScannerViewModel.Phase.READING_PHOTO
                || phase == ScannerViewModel.Phase.ERROR;
        scanStatus.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private int errorMessage(ProductRepository.Failure failure) {
        switch (failure) {
            case NOT_FOUND: return catalogSettings.read().usesCatalog()
                    ? R.string.catalog_product_not_found : R.string.product_not_found;
            case CONNECTION: return R.string.connection_error;
            case RATE_LIMIT: return R.string.rate_limit;
            case INVALID_RESPONSE: return R.string.invalid_response;
            default: return R.string.service_error;
        }
    }

    private void showPhotoResult(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            Toast.makeText(this, codes == null ? R.string.photo_error : R.string.photo_no_barcode,
                    Toast.LENGTH_LONG).show();
        } else if (codes.size() == 1) {
            model.submit(codes.get(0));
        } else {
            new AlertDialog.Builder(this).setTitle(R.string.photo_choose_barcode)
                    .setItems(codes.toArray(new String[0]), (dialog, which) -> model.submit(codes.get(which)))
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        }
    }

    private void showManualEntry() {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setHint(R.string.barcode_hint);
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(dp(24), dp(8), dp(24), 0);
        wrapper.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.manual_entry)
                .setView(wrapper)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.search, null)
                .create();
        dialog.setOnShowListener(unused -> {
            input.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                String barcode = BarcodeValue.normalize(input.getText().toString());
                if (barcode == null) {
                    input.setError(getString(R.string.invalid_barcode));
                } else {
                    dialog.dismiss();
                    model.submit(barcode);
                }
            });
            input.setOnEditorActionListener((view, action, event) -> {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            });
        });
        dialog.show();
    }

    private void showHistory(List<ScanRecord> records) {
        List<RecordPicker.Entry> entries = new ArrayList<>(records.size());
        for (ScanRecord record : records) {
            entries.add(listEntry(record.barcode, record.product, record.historyDate(), () -> {
                if (record.product == null) {
                    model.submit(record.barcode);
                } else {
                    model.openSaved(record);
                }
            }));
        }
        RecordPicker.show(this, R.string.history, R.string.empty_history, entries,
                () -> new AlertDialog.Builder(this)
                        .setTitle(R.string.clear_history)
                        .setMessage(R.string.clear_history_keep_saved)
                        .setNegativeButton(R.string.cancel, null)
                        .setPositiveButton(R.string.clear_history, (dialog, which) -> model.clearHistory())
                        .show());
    }

    private void showFavorites() {
        List<RecordPicker.Entry> entries = new ArrayList<>();
        for (FavoriteStore.Entry favorite : favorites.all()) {
            ScanRecord label = labels.find(favorite.barcode);
            ScanRecord snapshot = favorite.snapshot;
            ScanRecord available = snapshot;
            if (available == null || available.product == null || available.product.isPersonal()) {
                available = label;
            }
            ScanRecord result = available;
            entries.add(listEntry(favorite.barcode, label != null ? label.product
                    : result == null ? null : result.product, favorite.addedAt,
                    () -> {
                        if (result == null) {
                            model.submit(favorite.barcode);
                        } else {
                            model.openSaved(result);
                        }
                    }));
        }
        RecordPicker.show(this, R.string.favorites, R.string.no_favorites, entries, null);
    }

    private RecordPicker.Entry listEntry(String barcode, Product product, long savedAt, Runnable open) {
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        DateFormat date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale);
        String fallback = getString(R.string.unknown_product);
        String name = product == null ? fallback : product.name(locale, fallback);
        String brand = product != null && Product.hasText(product.brands) ? product.brands : "";
        return new RecordPicker.Entry(getString(R.string.history_item, name, barcode, date.format(new Date(savedAt))),
                name + " " + brand + " " + barcode, open);
    }

    private void showLanguages() {
        String[] tags = {"", "tr", "en"};
        String active = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        int selection = active.startsWith("tr") ? 1 : active.startsWith("en") ? 2 : 0;
        new AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setSingleChoiceItems(R.array.language_options, selection, (dialog, index) -> {
                    dialog.dismiss();
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags[index]));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
