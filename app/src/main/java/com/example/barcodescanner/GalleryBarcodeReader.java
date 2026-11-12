package com.example.barcodescanner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

final class GalleryBarcodeReader {
    private static final int MAX_DIMENSION = 2400;

    private GalleryBarcodeReader() {}

    static void read(Context context, Uri image, Consumer<List<String>> callback) {
        RequestManager images = Glide.with(context.getApplicationContext());
        Handler main = new Handler(Looper.getMainLooper());
        images.asBitmap().load(image)
                .centerInside()
                .disallowHardwareConfig()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .into(new CustomTarget<Bitmap>(MAX_DIMENSION, MAX_DIMENSION) {
                    @Override
                    public void onResourceReady(@NonNull Bitmap bitmap,
                            @Nullable Transition<? super Bitmap> transition) {
                        BarcodeScanner scanner = BarcodeAnalyzer.newScanner();
                        try {
                            scanner.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener(task -> {
                                scanner.close();
                                images.clear(this);
                                callback.accept(task.isSuccessful() ? codes(task.getResult()) : null);
                            });
                        } catch (RuntimeException e) {
                            scanner.close();
                            onLoadFailed(null);
                        }
                    }

                    @Override
                    public void onLoadFailed(@Nullable Drawable error) {
                        main.post(() -> {
                            images.clear(this);
                            callback.accept(null);
                        });
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                        // ML Kit keeps its input until processing completes.
                    }
                });
    }

    private static List<String> codes(List<Barcode> barcodes) {
        Map<String, String> codes = new LinkedHashMap<>();
        for (Barcode barcode : barcodes) {
            String code = BarcodeAnalyzer.codeOf(barcode);
            if (code != null) codes.putIfAbsent(BarcodeValue.identifier(code), code);
        }
        return new ArrayList<>(codes.values());
    }
}
