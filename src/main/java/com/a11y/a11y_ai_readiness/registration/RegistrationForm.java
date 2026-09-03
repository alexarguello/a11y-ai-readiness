package com.a11y.a11y_ai_readiness.registration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RegistrationForm {

    @NotBlank(message = "Enter your name.")
    private String name;

    @NotBlank(message = "Enter your email address.")
    @Email(message = "Enter a valid email address.")
    private String email;

    @NotNull(message = "Choose a meal preference.")
    private MealPreference mealPreference;

    @AssertTrue(message = "You must accept the terms to register.")
    private boolean acceptTerms;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public MealPreference getMealPreference() {
        return mealPreference;
    }

    public void setMealPreference(MealPreference mealPreference) {
        this.mealPreference = mealPreference;
    }

    public boolean isAcceptTerms() {
        return acceptTerms;
    }

    public void setAcceptTerms(boolean acceptTerms) {
        this.acceptTerms = acceptTerms;
    }
}
