package com.carddemo.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.TestFixtures;
import com.carddemo.persistence.entity.CardEntity;
import org.junit.jupiter.api.Test;

/** The record images the three print programs DISPLAY keep the length and layout of the copybooks. */
class MasterFileRecordsTest {

    @Test
    void laysTheCardRecordOutAsCvact02y() {
        CardEntity card = new CardEntity();
        card.setCardNum(TestFixtures.CARD_NUMBER);
        card.setCardAcctId(TestFixtures.ACCOUNT_ID);
        card.setCardCvvCd(123);
        card.setCardEmbossedName("JOHN Q PUBLIC");
        card.setCardExpiraionDate("2026-03-01");
        card.setCardActiveStatus("Y");

        String record = MasterFileRecords.cardRecord(card);

        assertThat(record).hasSize(150);
        assertThat(record).startsWith("4111111111111111" + "11111111111" + "123");
        assertThat(record.substring(30, 80)).isEqualTo("JOHN Q PUBLIC" + " ".repeat(37));
        assertThat(record.substring(80, 90)).isEqualTo("2026-03-01");
        assertThat(record.charAt(90)).isEqualTo('Y');
    }

    @Test
    void laysTheCrossReferenceRecordOutAsCvact03y() {
        String record = MasterFileRecords.xrefRecord(TestFixtures.xref());

        assertThat(record).hasSize(50);
        assertThat(record).startsWith("4111111111111111" + "100000001" + "11111111111");
        assertThat(record.substring(36)).isBlank();
    }

    @Test
    void laysTheCustomerRecordOutAsCvcus01y() {
        String record = MasterFileRecords.customerRecord(TestFixtures.customer());

        assertThat(record).hasSize(500);
        assertThat(record).startsWith("100000001John" + " ".repeat(21));
        assertThat(record.substring(234, 236)).isEqualTo("NY");
        assertThat(record.substring(279, 288)).isEqualTo("123456789");
        assertThat(record.substring(329, 332)).isEqualTo("720");
    }
}
