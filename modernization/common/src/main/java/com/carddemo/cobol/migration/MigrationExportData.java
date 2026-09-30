package com.carddemo.cobol.migration;

import java.math.BigDecimal;

/**
 * The payloads of the branch migration export record of {@code app/cpy/CVEXPORT.cpy}.
 *
 * <p>The copybook REDEFINES one 460 byte data area as five alternative layouts, one per record
 * type, so the converted code models the alternatives as separate records carried by the one
 * export record. Field names follow the copybook so the layouts stay traceable.
 */
public final class MigrationExportData {

    private MigrationExportData() {
    }

    /** {@code EXPORT-CUSTOMER-DATA}, record type 'C'. */
    public record Customer(long custId,
                           String firstName,
                           String middleName,
                           String lastName,
                           String addrLine1,
                           String addrLine2,
                           String addrLine3,
                           String addrStateCd,
                           String addrCountryCd,
                           String addrZip,
                           String phoneNum1,
                           String phoneNum2,
                           long ssn,
                           String govtIssuedId,
                           String dobYyyyMmDd,
                           String eftAccountId,
                           String priCardHolderInd,
                           int ficoCreditScore) {
    }

    /** {@code EXPORT-ACCOUNT-DATA}, record type 'A'. */
    public record Account(long acctId,
                          String activeStatus,
                          BigDecimal currBal,
                          BigDecimal creditLimit,
                          BigDecimal cashCreditLimit,
                          String openDate,
                          String expiraionDate,
                          String reissueDate,
                          BigDecimal currCycCredit,
                          BigDecimal currCycDebit,
                          String addrZip,
                          String groupId) {
    }

    /** {@code EXPORT-CARD-XREF-DATA}, record type 'X'. */
    public record Xref(String cardNum, long custId, long acctId) {
    }

    /** {@code EXPORT-TRANSACTION-DATA}, record type 'T'. */
    public record Transaction(String tranId,
                              String typeCd,
                              int catCd,
                              String source,
                              String description,
                              BigDecimal amount,
                              long merchantId,
                              String merchantName,
                              String merchantCity,
                              String merchantZip,
                              String cardNum,
                              String origTs,
                              String procTs) {
    }

    /** {@code EXPORT-CARD-DATA}, record type 'D'. */
    public record Card(String cardNum,
                       long acctId,
                       int cvvCd,
                       String embossedName,
                       String expiraionDate,
                       String activeStatus) {
    }
}
