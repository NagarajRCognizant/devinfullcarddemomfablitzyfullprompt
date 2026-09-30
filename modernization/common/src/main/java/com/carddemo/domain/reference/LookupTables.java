package com.carddemo.domain.reference;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

/**
 * The reference tables declared as 88-level VALUES lists in app/cpy/CSLKPCDY.cpy.
 *
 * <p>A COBOL condition name test compiles to a table lookup, so the values are kept as data
 * rather than as code: modernization/tools/extract_lookups.py regenerates the resource files
 * straight from the copybook, which keeps the lists traceable to the source.
 */
@Component
public class LookupTables {

    private final Set<String> phoneAreaCodes;
    private final Set<String> generalPurposeAreaCodes;
    private final Set<String> easilyRecognizableAreaCodes;
    private final Set<String> stateCodes;
    private final Set<String> stateZipCombos;

    public LookupTables() {
        this.phoneAreaCodes = load("reference/phone-area-codes.txt");
        this.generalPurposeAreaCodes = load("reference/phone-area-codes-general-purpose.txt");
        this.easilyRecognizableAreaCodes = load("reference/phone-area-codes-easily-recognizable.txt");
        this.stateCodes = load("reference/us-state-codes.txt");
        this.stateZipCombos = load("reference/us-state-zip2-combos.txt");
    }

    /** 88 VALID-PHONE-AREA-CODE. */
    public boolean isPhoneAreaCode(String value) {
        return phoneAreaCodes.contains(normalise(value));
    }

    /** 88 VALID-GENERAL-PURP-CODE - the table used by the phone number edit. */
    public boolean isGeneralPurposeAreaCode(String value) {
        return generalPurposeAreaCodes.contains(normalise(value));
    }

    /** 88 VALID-EASY-RECOG-AREA-CODE. */
    public boolean isEasilyRecognizableAreaCode(String value) {
        return easilyRecognizableAreaCodes.contains(normalise(value));
    }

    /** 88 VALID-US-STATE-CODE. */
    public boolean isStateCode(String value) {
        return stateCodes.contains(normalise(value));
    }

    /** 88 VALID-US-STATE-ZIP-CD2-COMBO - state code concatenated with the first two zip digits. */
    public boolean isStateZipCombo(String stateCode, String zipFirstTwoDigits) {
        return stateZipCombos.contains(normalise(stateCode) + normalise(zipFirstTwoDigits));
    }

    public int phoneAreaCodeCount() {
        return phoneAreaCodes.size();
    }

    public int generalPurposeAreaCodeCount() {
        return generalPurposeAreaCodes.size();
    }

    public int easilyRecognizableAreaCodeCount() {
        return easilyRecognizableAreaCodes.size();
    }

    public int stateCodeCount() {
        return stateCodes.size();
    }

    public int stateZipComboCount() {
        return stateZipCombos.size();
    }

    private static String normalise(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private static Set<String> load(String resource) {
        try {
            String content = StreamUtils.copyToString(
                    new ClassPathResource(resource).getInputStream(), StandardCharsets.UTF_8);
            Set<String> values = new LinkedHashSet<>();
            for (String line : content.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                    values.add(trimmed.toUpperCase());
                }
            }
            return values;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load reference table " + resource, e);
        }
    }
}
