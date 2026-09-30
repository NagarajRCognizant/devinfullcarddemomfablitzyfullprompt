package com.carddemo.batch;

import com.carddemo.cobol.CobolText;
import com.carddemo.persistence.entity.CardEntity;
import com.carddemo.persistence.entity.CardXrefEntity;
import com.carddemo.persistence.entity.CustomerEntity;

/**
 * The record images the three master file print programs DISPLAY.
 *
 * <p>CBACT02C, CBACT03C and CBCUS01C each read one key sequenced data set sequentially and DISPLAY
 * the whole record group: the card record of {@code CVACT02Y} (150 bytes), the cross reference
 * record of {@code CVACT03Y} (50 bytes) and the customer record of {@code CVCUS01Y} (500 bytes).
 * The operator saw the record laid out by its picture clauses, so the image is assembled here in
 * the same way rather than the field values being logged individually - the trailing FILLER
 * included, because it is part of what the terminal showed.
 */
public final class MasterFileRecords {

    private MasterFileRecords() {
    }

    /** CARD-RECORD of app/cpy/CVACT02Y.cpy. */
    public static String cardRecord(CardEntity card) {
        return CobolText.padRight(card.getCardNum(), 16)
                + CobolText.padLeftZero(Long.toString(card.getCardAcctId()), 11)
                + CobolText.padLeftZero(Integer.toString(card.getCardCvvCd()), 3)
                + CobolText.padRight(card.getCardEmbossedName(), 50)
                + CobolText.padRight(card.getCardExpiraionDate(), 10)
                + CobolText.padRight(card.getCardActiveStatus(), 1)
                + " ".repeat(59);
    }

    /** CARD-XREF-RECORD of app/cpy/CVACT03Y.cpy. */
    public static String xrefRecord(CardXrefEntity xref) {
        return CobolText.padRight(xref.getXrefCardNum(), 16)
                + CobolText.padLeftZero(Long.toString(xref.getXrefCustId()), 9)
                + CobolText.padLeftZero(Long.toString(xref.getXrefAcctId()), 11)
                + " ".repeat(14);
    }

    /** CUSTOMER-RECORD of app/cpy/CVCUS01Y.cpy. */
    public static String customerRecord(CustomerEntity customer) {
        return CobolText.padLeftZero(Long.toString(customer.getCustId()), 9)
                + CobolText.padRight(customer.getFirstName(), 25)
                + CobolText.padRight(customer.getMiddleName(), 25)
                + CobolText.padRight(customer.getLastName(), 25)
                + CobolText.padRight(customer.getAddrLine1(), 50)
                + CobolText.padRight(customer.getAddrLine2(), 50)
                + CobolText.padRight(customer.getAddrLine3(), 50)
                + CobolText.padRight(customer.getAddrStateCd(), 2)
                + CobolText.padRight(customer.getAddrCountryCd(), 3)
                + CobolText.padRight(customer.getAddrZip(), 10)
                + CobolText.padRight(customer.getPhoneNum1(), 15)
                + CobolText.padRight(customer.getPhoneNum2(), 15)
                + CobolText.padLeftZero(CobolText.trim(customer.getSsn()), 9)
                + CobolText.padRight(customer.getGovtIssuedId(), 20)
                + CobolText.padRight(customer.getDobYyyyMmDd(), 10)
                + CobolText.padRight(customer.getEftAccountId(), 10)
                + CobolText.padRight(customer.getPriCardHolderInd(), 1)
                + CobolText.padLeftZero(Integer.toString(customer.getFicoCreditScore()), 3)
                + " ".repeat(168);
    }
}
