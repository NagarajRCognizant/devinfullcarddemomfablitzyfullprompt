package com.carddemo.cobol;

/**
 * Helpers reproducing the COBOL character semantics the account programs rely on.
 *
 * <p>The legacy screens distinguish three states for every input field: LOW-VALUES
 * (never keyed / cleared), SPACES (keyed blank) and a value. The two states that mean
 * "nothing was supplied" collapse to {@code null} or blank in the modernized API, so
 * {@link #isBlank(String)} is the single test used by the ported edit routines.
 */
public final class CobolText {

    private CobolText() {
    }

    /** True for the COBOL {@code EQUAL LOW-VALUES OR EQUAL SPACES} test. */
    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * The receive-map logic of COACTVWC and COACTUPC moves LOW-VALUES into an input field whose
     * keyed value is a single asterisk or all spaces, which is how the maps let a user clear a
     * field. An asterisk therefore means "nothing supplied" and not the literal character.
     */
    public static String normaliseAsterisk(String value) {
        if (value == null) {
            return null;
        }
        return "*".equals(value.trim()) ? "" : value;
    }

    /** COBOL {@code FUNCTION TRIM}. */
    public static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    /** COBOL {@code FUNCTION UPPER-CASE(FUNCTION TRIM(x))}. */
    public static String upperTrim(String value) {
        return trim(value).toUpperCase();
    }

    /** COBOL {@code IS NUMERIC} on a display field: every character must be a digit. */
    public static boolean isNumeric(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** True when every character is a letter or a space (COBOL alphabetic edit). */
    public static boolean isAlphabeticOrSpace(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetter(c) && c != ' ') {
                return false;
            }
        }
        return true;
    }

    /** True when every character is a letter, digit or space (COBOL alphanumeric edit). */
    public static boolean isAlphanumericOrSpace(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != ' ') {
                return false;
            }
        }
        return true;
    }

    /** True when the trimmed value is all zeroes, the COBOL {@code EQUAL ZEROS} test. */
    public static boolean isAllZeroes(String value) {
        String trimmed = trim(value);
        if (trimmed.isEmpty()) {
            return false;
        }
        return trimmed.chars().allMatch(c -> c == '0');
    }

    /** Fixed-length right padding, as a COBOL {@code MOVE} into a PIC X(n) field. */
    public static String padRight(String value, int length) {
        String source = value == null ? "" : value;
        if (source.length() >= length) {
            return source.substring(0, length);
        }
        return source + " ".repeat(length - source.length());
    }

    /** Right justification with blank fill, as produced by a zero suppressed numeric edit. */
    public static String padLeft(String value, int length) {
        String source = value == null ? "" : value;
        if (source.length() >= length) {
            return source;
        }
        return " ".repeat(length - source.length()) + source;
    }

    /** Left-zero padding used when a screen field feeds a PIC 9(n) field. */
    public static String padLeftZero(String value, int length) {
        String source = trim(value);
        if (source.length() >= length) {
            return source.substring(source.length() - length);
        }
        return "0".repeat(length - source.length()) + source;
    }
}
