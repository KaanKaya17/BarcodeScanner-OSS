package com.example.barcodescanner;

import java.util.function.BiConsumer;
import java.util.function.Function;

enum Nutrient {
    ENERGY(R.string.label_calories, R.id.tvCalories, R.string.unit_kcal, 1,
            n -> n.caloriesPer100g, (n, v) -> n.caloriesPer100g = v),
    PROTEIN(R.string.label_protein, R.id.tvProtein, R.string.unit_gram, 0.1,
            n -> n.proteinPer100g, (n, v) -> n.proteinPer100g = v),
    FAT(R.string.label_fat, R.id.tvFat, R.string.unit_gram, 0.1,
            n -> n.fatPer100g, (n, v) -> n.fatPer100g = v),
    CARBS(R.string.label_carbs, R.id.tvCarbs, R.string.unit_gram, 0.1,
            n -> n.carbsPer100g, (n, v) -> n.carbsPer100g = v),
    SUGAR(R.string.label_sugar, R.id.tvSugar, R.string.unit_gram, 0.1,
            n -> n.sugarsPer100g, (n, v) -> n.sugarsPer100g = v),
    FIBER(R.string.label_fiber, R.id.tvFiber, R.string.unit_gram, 0.1,
            n -> n.fiberPer100g, (n, v) -> n.fiberPer100g = v),
    SALT(R.string.label_salt, R.id.tvSalt, R.string.unit_gram, 0.1,
            n -> n.saltPer100g, (n, v) -> n.saltPer100g = v);

    final int label;
    final int view;
    final int unit;
    final Function<Nutriments, Double> read;
    final BiConsumer<Nutriments, Double> write;
    final double tolerance;

    Nutrient(int label, int view, int unit, double tolerance,
            Function<Nutriments, Double> read, BiConsumer<Nutriments, Double> write) {
        this.label = label;
        this.view = view;
        this.unit = unit;
        this.tolerance = tolerance;
        this.read = read;
        this.write = write;
    }
}
