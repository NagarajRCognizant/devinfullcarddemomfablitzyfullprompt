package com.carddemo.transaction.domain;

/**
 * The message literals of the transaction screens, kept verbatim.
 *
 * <p>Every constant is one {@code MOVE ... TO WS-MESSAGE} of app/cbl/COTRN00C.cbl (list),
 * app/cbl/COTRN01C.cbl (view), app/cbl/COTRN02C.cbl (add), app/cbl/COBIL00C.cbl (bill payment) or
 * app/cbl/CORPT00C.cbl (report request). The texts are observable behaviour of the screens, so
 * they are reproduced exactly, including the trailing ellipses and the irregular spacing.
 */
public final class TransactionMessages {

    private TransactionMessages() {
    }

    // ------------------------------------------------------------------ COTRN00C (Transaction List)

    /** PROCESS-ENTER-KEY: a selection flag other than S or s. */
    public static final String INVALID_SELECTION = "Invalid selection. Valid value is S";
    /** PROCESS-ENTER-KEY: the transaction identifier filter is not numeric. */
    public static final String TRAN_ID_NOT_NUMERIC = "Tran ID must be Numeric ...";
    /** PROCESS-PF7-KEY: already on the first page. */
    public static final String ALREADY_AT_TOP = "You are already at the top of the page...";
    /** PROCESS-PF8-KEY: already on the last page. */
    public static final String ALREADY_AT_BOTTOM = "You are already at the bottom of the page...";
    /** PROCESS-PAGE-BACKWARD: the first page has just been reached. */
    public static final String AT_TOP = "You are at the top of the page...";
    /** PROCESS-PAGE-FORWARD: the last page has just been reached. */
    public static final String REACHED_BOTTOM = "You have reached the bottom of the page...";
    /** PROCESS-PAGE-BACKWARD: paging back from the first page. */
    public static final String REACHED_TOP = "You have reached the top of the page...";
    /** STARTBR/READNEXT failure on TRANSACT; the list program spells it in lower case. */
    public static final String UNABLE_TO_LOOKUP_TRANSACTION_LIST = "Unable to lookup transaction...";

    // ------------------------------------------------------------------ COTRN01C (Transaction View)

    /** PROCESS-ENTER-KEY: no identifier keyed. */
    public static final String TRAN_ID_EMPTY = "Tran ID can NOT be empty...";
    /** READ-TRANSACT-FILE: DFHRESP(NOTFND). */
    public static final String TRANSACTION_NOT_FOUND = "Transaction ID NOT found...";
    /** READ-TRANSACT-FILE: any other CICS response. */
    public static final String UNABLE_TO_LOOKUP_TRANSACTION = "Unable to lookup Transaction...";

    // ------------------------------------------------------------------ COTRN02C (Transaction Add)

    /** VALIDATE-INPUT-KEY-FIELDS: neither key was supplied. */
    public static final String ACCOUNT_OR_CARD_REQUIRED = "Account or Card Number must be entered...";
    public static final String ACCOUNT_ID_NOT_NUMERIC = "Account ID must be Numeric...";
    public static final String CARD_NUMBER_NOT_NUMERIC = "Card Number must be Numeric...";
    /** READ-CXACAIX-FILE: DFHRESP(NOTFND). */
    public static final String ACCOUNT_NOT_FOUND = "Account ID NOT found...";
    public static final String UNABLE_TO_LOOKUP_ACCOUNT_XREF = "Unable to lookup Acct in XREF AIX file...";
    /** READ-CXACAIX-FILE / READ-CCXREF-FILE: DFHRESP(NOTFND) on the card key. */
    public static final String CARD_NOT_FOUND = "Card Number NOT found...";
    public static final String UNABLE_TO_LOOKUP_CARD_XREF = "Unable to lookup Card # in XREF file...";

    public static final String TYPE_CD_EMPTY = "Type CD can NOT be empty...";
    public static final String CATEGORY_CD_EMPTY = "Category CD can NOT be empty...";
    public static final String SOURCE_EMPTY = "Source can NOT be empty...";
    public static final String DESCRIPTION_EMPTY = "Description can NOT be empty...";
    public static final String AMOUNT_EMPTY = "Amount can NOT be empty...";
    public static final String ORIG_DATE_EMPTY = "Orig Date can NOT be empty...";
    public static final String PROC_DATE_EMPTY = "Proc Date can NOT be empty...";
    public static final String MERCHANT_ID_EMPTY = "Merchant ID can NOT be empty...";
    public static final String MERCHANT_NAME_EMPTY = "Merchant Name can NOT be empty...";
    public static final String MERCHANT_CITY_EMPTY = "Merchant City can NOT be empty...";
    public static final String MERCHANT_ZIP_EMPTY = "Merchant Zip can NOT be empty...";

    public static final String TYPE_CD_NOT_NUMERIC = "Type CD must be Numeric...";
    public static final String CATEGORY_CD_NOT_NUMERIC = "Category CD must be Numeric...";
    public static final String MERCHANT_ID_NOT_NUMERIC = "Merchant ID must be Numeric...";
    public static final String AMOUNT_FORMAT = "Amount should be in format -99999999.99";
    public static final String ORIG_DATE_FORMAT = "Orig Date should be in format YYYY-MM-DD";
    public static final String PROC_DATE_FORMAT = "Proc Date should be in format YYYY-MM-DD";
    public static final String ORIG_DATE_INVALID = "Orig Date - Not a valid date...";
    public static final String PROC_DATE_INVALID = "Proc Date - Not a valid date...";

    /** PROCESS-ENTER-KEY: confirmation is N, n or blank. */
    public static final String CONFIRM_ADD = "Confirm to add this transaction...";
    /** PROCESS-ENTER-KEY: confirmation is anything else, shared with COBIL00C. */
    public static final String INVALID_CONFIRMATION = "Invalid value. Valid values are (Y/N)...";
    /** WRITE-TRANSACT-FILE: DFHRESP(DUPREC). */
    public static final String TRAN_ID_ALREADY_EXISTS = "Tran ID already exist...";
    /** WRITE-TRANSACT-FILE: any other CICS response. */
    public static final String UNABLE_TO_ADD_TRANSACTION = "Unable to Add Transaction...";

    /**
     * WRITE-TRANSACT-FILE on success. The STRING statement concatenates a literal that ends in a
     * space with one that begins with a space, so the message has two spaces after the full stop.
     */
    public static String transactionAdded(String tranId) {
        return "Transaction added successfully.  Your Tran ID is " + tranId + ".";
    }

    // ------------------------------------------------------------------ COBIL00C (Bill Payment)

    public static final String ACCT_ID_EMPTY = "Acct ID can NOT be empty...";
    public static final String NOTHING_TO_PAY = "You have nothing to pay...";
    public static final String CONFIRM_PAYMENT = "Confirm to make a bill payment...";
    public static final String UNABLE_TO_LOOKUP_ACCOUNT = "Unable to lookup Account...";
    public static final String UNABLE_TO_UPDATE_ACCOUNT = "Unable to Update Account...";
    public static final String UNABLE_TO_LOOKUP_XREF_AIX = "Unable to lookup XREF AIX file...";
    public static final String UNABLE_TO_ADD_BILL_PAY_TRANSACTION = "Unable to Add Bill pay Transaction...";

    /** WRITE-TRANSACT-FILE of COBIL00C on success, with the same doubled space as the add screen. */
    public static String paymentSuccessful(String tranId) {
        return "Payment successful.  Your Transaction ID is " + tranId + ".";
    }

    // ------------------------------------------------------------------ CORPT00C (Report Request)

    public static final String START_DATE_MONTH_EMPTY = "Start Date - Month can NOT be empty...";
    public static final String START_DATE_DAY_EMPTY = "Start Date - Day can NOT be empty...";
    public static final String START_DATE_YEAR_EMPTY = "Start Date - Year can NOT be empty...";
    public static final String END_DATE_MONTH_EMPTY = "End Date - Month can NOT be empty...";
    public static final String END_DATE_DAY_EMPTY = "End Date - Day can NOT be empty...";
    public static final String END_DATE_YEAR_EMPTY = "End Date - Year can NOT be empty...";
    public static final String START_DATE_MONTH_INVALID = "Start Date - Not a valid Month...";
    public static final String START_DATE_DAY_INVALID = "Start Date - Not a valid Day...";
    public static final String START_DATE_YEAR_INVALID = "Start Date - Not a valid Year...";
    public static final String END_DATE_MONTH_INVALID = "End Date - Not a valid Month...";
    public static final String END_DATE_DAY_INVALID = "End Date - Not a valid Day...";
    public static final String END_DATE_YEAR_INVALID = "End Date - Not a valid Year...";
    public static final String START_DATE_INVALID = "Start Date - Not a valid date...";
    public static final String END_DATE_INVALID = "End Date - Not a valid date...";

    /** PROCESS-ENTER-KEY: none of the three report type fields was keyed. */
    public static final String SELECT_REPORT_TYPE = "Select a report type to print report...";
    /** WIRTE-JOBSUB-TDQ: the write to the JOBS transient data queue failed. */
    public static final String UNABLE_TO_WRITE_TDQ = "Unable to Write TDQ (JOBS)...";

    /** PROCESS-ENTER-KEY builds this with STRING once the job has been submitted. */
    public static String reportSubmitted(String reportName) {
        return reportName + " report submitted for printing ...";
    }

    /** SUBMIT-JOB-TO-INTRDR builds this prompt with STRING when no confirmation was keyed. */
    public static String confirmPrint(String reportName) {
        return "Please confirm to print the " + reportName + " report...";
    }

    /** SUBMIT-JOB-TO-INTRDR builds this message with STRING for any confirmation other than Y or N. */
    public static String invalidPrintConfirmation(String keyed) {
        return "\"" + keyed + "\" is not a valid value to confirm...";
    }
}
