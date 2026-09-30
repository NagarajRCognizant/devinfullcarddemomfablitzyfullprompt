package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * CARDDAT VSAM KSDS record (app/cpy/CVACT02Y.cpy, RECLN 150, KEYS(16 0)).
 *
 * <p>The card number is the key, and CARDAIX is the alternate index over CARD-ACCT-ID that
 * COCRDLIC uses to list the cards of one account. The expiry date is the ten character text the
 * record stores; COCRDUPC edits it as three separate screen fields but rewrites the same text.
 */
@Entity
@Table(name = "card")
public class CardEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_num", length = 16, nullable = false, columnDefinition = "char(16)")
    private String cardNum;

    @Column(name = "card_acct_id", nullable = false)
    private Long cardAcctId;

    @Column(name = "card_cvv_cd", nullable = false)
    private Integer cardCvvCd;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_embossed_name", length = 50, nullable = false, columnDefinition = "char(50)")
    private String cardEmbossedName;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_expiraion_date", length = 10, nullable = false, columnDefinition = "char(10)")
    private String cardExpiraionDate;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_active_status", length = 1, nullable = false, columnDefinition = "char(1)")
    private String cardActiveStatus;

    public String getCardNum() {
        return cardNum;
    }

    public void setCardNum(String cardNum) {
        this.cardNum = cardNum;
    }

    public Long getCardAcctId() {
        return cardAcctId;
    }

    public void setCardAcctId(Long cardAcctId) {
        this.cardAcctId = cardAcctId;
    }

    public Integer getCardCvvCd() {
        return cardCvvCd;
    }

    public void setCardCvvCd(Integer cardCvvCd) {
        this.cardCvvCd = cardCvvCd;
    }

    public String getCardEmbossedName() {
        return cardEmbossedName;
    }

    public void setCardEmbossedName(String cardEmbossedName) {
        this.cardEmbossedName = cardEmbossedName;
    }

    /** CARD-EXPIRAION-DATE, spelled as in the copybook. */
    public String getCardExpiraionDate() {
        return cardExpiraionDate;
    }

    public void setCardExpiraionDate(String cardExpiraionDate) {
        this.cardExpiraionDate = cardExpiraionDate;
    }

    public String getCardActiveStatus() {
        return cardActiveStatus;
    }

    public void setCardActiveStatus(String cardActiveStatus) {
        this.cardActiveStatus = cardActiveStatus;
    }
}
