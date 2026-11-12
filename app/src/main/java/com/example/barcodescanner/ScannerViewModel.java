package com.example.barcodescanner;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.Collections;
import java.util.List;

import retrofit2.Call;

public final class ScannerViewModel extends AndroidViewModel {
    enum Phase { READY, READING_PHOTO, PHOTO_RESULT, LOADING, RESULT, ERROR }

    static final class State {
        final Phase phase;
        final String barcode;
        final ProductRepository.Result result;
        final List<String> photoCodes;

        State(Phase phase, String barcode, ProductRepository.Result result) {
            this(phase, barcode, result, null);
        }

        State(Phase phase, String barcode, ProductRepository.Result result, List<String> photoCodes) {
            this.phase = phase;
            this.barcode = barcode;
            this.result = result;
            this.photoCodes = photoCodes;
        }
    }

    private static final State READY = new State(Phase.READY, null, null);

    private final ProductRepository repository;
    private final ScanGate gate = new ScanGate();
    private final MutableLiveData<State> state = new MutableLiveData<>(READY);
    private final MutableLiveData<List<ScanRecord>> history = new MutableLiveData<>(Collections.emptyList());
    private Call<FoodResponse> active;
    private int photoGeneration;

    public ScannerViewModel(@NonNull Application application) {
        super(application);
        repository = ProductRepository.get(application);
    }

    LiveData<State> state() {
        return state;
    }

    LiveData<List<ScanRecord>> history() {
        return history;
    }

    void onFrame(String barcode, long now) {
        if (state.getValue().phase == Phase.READY && gate.accept(barcode, now)) {
            start(barcode);
        }
    }

    void submit(String barcode) {
        Phase phase = state.getValue().phase;
        if (phase == Phase.READY || phase == Phase.ERROR) {
            gate.hold(barcode);
            start(barcode);
        }
    }

    void scanPhoto(Uri uri) {
        Phase phase = state.getValue().phase;
        if (phase != Phase.READY && phase != Phase.ERROR) return;
        int request = ++photoGeneration;
        state.setValue(new State(Phase.READING_PHOTO, null, null));
        GalleryBarcodeReader.read(getApplication(), uri, codes -> {
            if (request == photoGeneration) {
                state.setValue(new State(Phase.PHOTO_RESULT, null, null, codes));
            }
        });
    }

    void openSaved(ScanRecord record) {
        Phase phase = state.getValue().phase;
        if (phase == Phase.READY || phase == Phase.ERROR) {
            gate.hold(record.barcode);
            state.setValue(new State(Phase.RESULT, record.barcode, ProductRepository.Result.found(record, true)));
        }
    }

    void retry() {
        State current = state.getValue();
        if (current.phase == Phase.ERROR) {
            submit(current.barcode);
        }
    }

    void reset() {
        photoGeneration++;
        cancelActive();
        state.setValue(READY);
    }

    void loadHistory() {
        repository.history(history::setValue);
    }

    void clearHistory() {
        repository.clearHistory(this::loadHistory);
    }

    private void start(String barcode) {
        state.setValue(new State(Phase.LOADING, barcode, null));
        active = repository.lookup(barcode, result -> {
            active = null;
            Phase phase = result.record == null ? Phase.ERROR : Phase.RESULT;
            state.setValue(new State(phase, barcode, result));
        });
    }

    private void cancelActive() {
        if (active != null) {
            active.cancel();
            active = null;
        }
    }

    @Override
    protected void onCleared() {
        photoGeneration++;
        cancelActive();
    }
}
