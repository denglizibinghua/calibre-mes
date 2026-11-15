package com.bikeassembly.utils;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import java.util.Locale;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Deliberately failing specification regression; run separately while production is unchanged. */
@Tag("known-defect")
@ResourceLock("default-locale")
class PasswordValidatorKnownDefectReproduction {
    @Test
    void rejectsUsernameRegardlessOfTurkishDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertFalse(PasswordValidator.validate("BIKEuser@2026", "bikeuser", 8, 32).valid,
                    "A password containing the username must be rejected in every locale");
        } finally {
            Locale.setDefault(original);
        }
    }
}
