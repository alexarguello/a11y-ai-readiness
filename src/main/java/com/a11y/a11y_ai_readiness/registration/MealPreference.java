package com.a11y.a11y_ai_readiness.registration;

public enum MealPreference {

    STANDARD("Standard"),
    VEGETARIAN("Vegetarian"),
    VEGAN("Vegan"),
    GLUTEN_FREE("Gluten-free");

    private final String label;

    MealPreference(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
