package com.example.barcodescanner;

final class ScanGate {
    private static final long REARM_AFTER_MS = 600;

    private String lastAccepted;
    private long missingSince = -1;

    boolean accept(String barcode, long now) {
        if (barcode == null) {
            if (missingSince < 0) {
                missingSince = now;
            } else if (now - missingSince >= REARM_AFTER_MS) {
                lastAccepted = null;
            }
            return false;
        }

        missingSince = -1;
        if (barcode.equals(lastAccepted)) {
            return false;
        }
        lastAccepted = barcode;
        return true;
    }

    void hold(String barcode) {
        lastAccepted = barcode;
        missingSince = -1;
    }
}
