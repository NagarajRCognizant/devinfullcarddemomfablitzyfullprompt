package com.carddemo.transaction.service;

import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.transaction.api.dto.TransactionAddRequest;
import com.carddemo.transaction.client.CardholderView;
import java.math.BigDecimal;

/** Records shaped like the supplied sample data, for the tests of this context. */
final class TransactionFixtures {

    static final String CARD_NUMBER = "4859452612877065";
    static final long ACCOUNT_ID = 1L;

    private TransactionFixtures() {
    }

    static TransactionEntity transaction(String tranId) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setTranId(tranId);
        transaction.setTranTypeCd("01");
        transaction.setTranCatCd(1);
        transaction.setTranSource("POS TERM");
        transaction.setTranDesc("Purchase at Abshire-Lowe");
        transaction.setTranAmt(new BigDecimal("504.77"));
        transaction.setTranMerchantId(800000000L);
        transaction.setTranMerchantName("Abshire-Lowe");
        transaction.setTranMerchantCity("North Enoshaven");
        transaction.setTranMerchantZip("72112");
        transaction.setTranCardNum(CARD_NUMBER);
        transaction.setTranOrigTs("2022-06-10-19.27.53.000000");
        transaction.setTranProcTs("2022-06-10-19.27.53.000000");
        return transaction;
    }

    static CardholderView cardholder(BigDecimal balance) {
        return CardholderView.found(CARD_NUMBER, ACCOUNT_ID, balance);
    }

    /** A request that passes every edit of VALIDATE-INPUT-DATA-FIELDS. */
    static TransactionAddRequest validAddRequest(String confirm) {
        return new TransactionAddRequest("00000000001", null, "01", "0001", "POS TERM",
                "Purchase at Abshire-Lowe", "+00000504.77", "2022-06-10", "2022-06-10",
                "800000000", "Abshire-Lowe", "North Enoshaven", "72112", confirm);
    }
}
