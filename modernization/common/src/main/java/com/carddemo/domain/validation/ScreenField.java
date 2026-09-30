package com.carddemo.domain.validation;

/**
 * Names of the editable fields of the COACTUP map (app/bms/COACTUP.bms).
 *
 * <p>One constant per BMS input field, so the flags returned by the API line up with the
 * fields the React form renders and highlights.
 */
public final class ScreenField {

    public static final String ACCOUNT_ID = "accountId";
    public static final String ACTIVE_STATUS = "activeStatus";
    public static final String OPEN_YEAR = "openYear";
    public static final String OPEN_MONTH = "openMonth";
    public static final String OPEN_DAY = "openDay";
    public static final String EXPIRY_YEAR = "expiryYear";
    public static final String EXPIRY_MONTH = "expiryMonth";
    public static final String EXPIRY_DAY = "expiryDay";
    public static final String REISSUE_YEAR = "reissueYear";
    public static final String REISSUE_MONTH = "reissueMonth";
    public static final String REISSUE_DAY = "reissueDay";
    public static final String CREDIT_LIMIT = "creditLimit";
    public static final String CASH_CREDIT_LIMIT = "cashCreditLimit";
    public static final String CURRENT_BALANCE = "currentBalance";
    public static final String CURRENT_CYCLE_CREDIT = "currentCycleCredit";
    public static final String CURRENT_CYCLE_DEBIT = "currentCycleDebit";
    public static final String GROUP_ID = "groupId";
    public static final String CUSTOMER_ID = "customerId";
    public static final String SSN_PART1 = "ssnPart1";
    public static final String SSN_PART2 = "ssnPart2";
    public static final String SSN_PART3 = "ssnPart3";
    public static final String DOB_YEAR = "dobYear";
    public static final String DOB_MONTH = "dobMonth";
    public static final String DOB_DAY = "dobDay";
    public static final String FICO_SCORE = "ficoScore";
    public static final String FIRST_NAME = "firstName";
    public static final String MIDDLE_NAME = "middleName";
    public static final String LAST_NAME = "lastName";
    public static final String ADDRESS_LINE_1 = "addressLine1";
    public static final String ADDRESS_LINE_2 = "addressLine2";
    public static final String CITY = "city";
    public static final String STATE = "state";
    public static final String ZIP = "zip";
    public static final String COUNTRY = "country";
    public static final String PHONE_1_AREA = "phone1Area";
    public static final String PHONE_1_PREFIX = "phone1Prefix";
    public static final String PHONE_1_LINE = "phone1Line";
    public static final String PHONE_2_AREA = "phone2Area";
    public static final String PHONE_2_PREFIX = "phone2Prefix";
    public static final String PHONE_2_LINE = "phone2Line";
    public static final String GOVERNMENT_ISSUED_ID = "governmentIssuedId";
    public static final String EFT_ACCOUNT_ID = "eftAccountId";
    public static final String PRIMARY_CARD_HOLDER = "primaryCardHolder";

    private ScreenField() {
    }
}
