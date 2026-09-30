package com.carddemo.batch.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The SYSIN parameter card of CBPAUP0J ({@code 00,00001,00001,Y}) as externalized configuration.
 *
 * <p>Paragraph 1000-INITIALIZE reads {@code PRM-INFO} from SYSIN and defaults each field when it is
 * unusable; those defaults are reproduced here as the property defaults, and the parameter card is
 * supplied as {@code --carddemo.batch.authpurge.expiry-days=...} on the job submission instead.
 */
@ConfigurationProperties(prefix = "carddemo.batch.authpurge")
public class AuthPurgeProperties {

    /** P-EXPIRY-DAYS, defaulted to 5 by 1000-INITIALIZE when the card field is not numeric. */
    private int expiryDays = 5;

    /**
     * P-CHKP-FREQ, defaulted to 5 when the card field is blank, zero or low values. The source
     * takes an IMS CHKP after this many summary segments; here it is the chunk commit interval,
     * which is the point the step's transaction commits and the reader's position is saved.
     */
    private int checkpointFrequency = 5;

    /** P-CHKP-DIS-FREQ, defaulted to 10: how many checkpoints between progress messages. */
    private int checkpointDisplayFrequency = 10;

    /** P-DEBUG-FLAG: 'Y' only when passed explicitly, otherwise normalized to 'N'. */
    private boolean debug;

    /**
     * Controls the remediation of DEF-AUTH-03. Paragraph 4000-CHECK-IF-EXPIRED decrements the
     * summary counters in working storage, but the program never issues a REPL for PAUTSUM0, so on
     * the mainframe those decrements are discarded unless the root is deleted and the available
     * credit the job is documented to adjust is never actually adjusted. The adjustment is
     * persisted here by default; setting this to false reproduces the AS-IS behaviour for
     * comparison runs. Both paths are covered by tests and recorded in the parity evidence.
     */
    private boolean persistSummaryAdjustments = true;

    public int getExpiryDays() {
        return expiryDays;
    }

    public void setExpiryDays(int expiryDays) {
        this.expiryDays = expiryDays;
    }

    public int getCheckpointFrequency() {
        return checkpointFrequency;
    }

    public void setCheckpointFrequency(int checkpointFrequency) {
        this.checkpointFrequency = checkpointFrequency <= 0 ? 5 : checkpointFrequency;
    }

    public int getCheckpointDisplayFrequency() {
        return checkpointDisplayFrequency;
    }

    public void setCheckpointDisplayFrequency(int checkpointDisplayFrequency) {
        this.checkpointDisplayFrequency =
                checkpointDisplayFrequency <= 0 ? 10 : checkpointDisplayFrequency;
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public boolean isPersistSummaryAdjustments() {
        return persistSummaryAdjustments;
    }

    public void setPersistSummaryAdjustments(boolean persistSummaryAdjustments) {
        this.persistSummaryAdjustments = persistSummaryAdjustments;
    }
}
