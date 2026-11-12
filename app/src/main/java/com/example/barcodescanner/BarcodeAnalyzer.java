package com.example.barcodescanner;

import android.graphics.Rect;

import androidx.camera.core.ImageAnalysis;
import androidx.camera.mlkit.vision.MlKitAnalyzer;

import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

final class BarcodeAnalyzer implements AutoCloseable {
    private final BarcodeScanner scanner = newScanner();

    static BarcodeScanner newScanner() {
        return BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
            .build());
    }

    static String codeOf(Barcode barcode) {
        return barcode.getFormat() == Barcode.FORMAT_UPC_E
                ? BarcodeValue.expandUpcE(barcode.getRawValue())
                : BarcodeValue.normalize(barcode.getRawValue());
    }

    ImageAnalysis.Analyzer create(Executor callbackExecutor, Consumer<List<Barcode>> callback) {
        return new MlKitAnalyzer(Collections.singletonList(scanner),
                ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED, callbackExecutor, result -> {
                    if (result.getThrowable(scanner) == null) {
                        List<Barcode> barcodes = result.getValue(scanner);
                        if (barcodes != null) {
                            callback.accept(barcodes);
                        }
                    }
                });
    }

    static String closestBarcode(List<Barcode> barcodes, Rect frame) {
        String closest = null;
        double shortest = Double.POSITIVE_INFINITY;
        for (Barcode barcode : barcodes) {
            Rect bounds = barcode.getBoundingBox();
            if (bounds == null || !frame.contains(bounds.centerX(), bounds.centerY())) {
                continue;
            }
            String code = codeOf(barcode);
            if (code != null) {
                double distance = Math.hypot(bounds.centerX() - frame.centerX(), bounds.centerY() - frame.centerY());
                if (distance < shortest) {
                    closest = code;
                    shortest = distance;
                }
            }
        }
        return closest;
    }

    @Override
    public void close() {
        scanner.close();
    }
}
