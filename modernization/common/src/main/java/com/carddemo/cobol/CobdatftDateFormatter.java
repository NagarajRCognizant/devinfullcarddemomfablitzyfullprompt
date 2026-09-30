package com.carddemo.cobol;

/**
 * Port of assembler module COBDATFT (app/asm/COBDATFT.asm), which CBACT01C calls to reformat the
 * reissue date before writing the extract record.
 *
 * <p>The module is a pure string rearrangement over the CODATECN interface: it validates the input
 * and output type bytes, moves the digits and, on any type mismatch, only writes "INVALID INPUT"
 * into the error field of the interface. CBACT01C never inspects that field, so a rejected
 * conversion leaves the previous content of the output area in place - that behaviour is preserved
 * here by returning the untouched output area.
 *
 * <p>Only the combination CBACT01C uses (input type 2, output type 2) occurs in the supplied
 * source; the other supported combination is implemented because it is unambiguous in the
 * assembler. The commented out separator check in the VALIDIN2 path is recorded as dead code in
 * the Unsupported Construct Register and is not reinstated.
 */
public final class CobdatftDateFormatter {

    /** Length of CODATECN-0UT-DATE. */
    public static final int OUTPUT_LENGTH = 20;

    public static final String INVALID_INPUT = "INVALID INPUT";

    private CobdatftDateFormatter() {
    }

    /** Result of one call: the 20 byte output area and the error message field. */
    public record Result(String outputDate, String errorMessage) {
    }

    /**
     * @param inputType  CODATECN-TYPE: '1' for YYYYMMDD input, '2' for YYYY-MM-DD input
     * @param outputType CODATECN-OUTTYPE: '1' for YYYY-MM-DD output, '2' for YYYYMMDD output
     * @param inputDate  CODATECN-INP-DATE
     * @param outputArea the current content of CODATECN-0UT-DATE, which is left untouched on error
     */
    public static Result convert(char inputType, char outputType, String inputDate, String outputArea) {
        String input = CobolText.padRight(inputDate, OUTPUT_LENGTH);
        String current = CobolText.padRight(outputArea, OUTPUT_LENGTH);
        if (inputType == '1') {
            // VALIDIN1: a separator in position 5 or an output type of '2' is rejected.
            if (input.charAt(4) == '-' || outputType == '2') {
                return new Result(current, INVALID_INPUT);
            }
            String formatted = input.substring(0, 4) + '-' + input.substring(4, 6) + '-' + input.substring(6, 8);
            return new Result(overlay(current, formatted), "");
        }
        if (inputType == '2') {
            // VALIDIN2: only an output type of '1' is rejected.
            if (outputType == '1') {
                return new Result(current, INVALID_INPUT);
            }
            String formatted = input.substring(0, 4) + input.substring(5, 7) + input.substring(8, 10);
            return new Result(overlay(current, formatted), "");
        }
        return new Result(current, INVALID_INPUT);
    }

    /** The assembler moves into the first bytes of the output area and leaves the rest as it was. */
    private static String overlay(String area, String value) {
        return value + area.substring(value.length());
    }
}
