package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Primary key of DB2 table CARDDEMO.AUTHFRDS: {@code PRIMARY KEY(CARD_NUM, AUTH_TS)}. */
@Embeddable
public class FraudReportId implements Serializable {

    private static final long serialVersionUID = 1L;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "card_num", length = 16, nullable = false, columnDefinition = "char(16)")
    private String cardNum;

    @Column(name = "auth_ts", nullable = false)
    private LocalDateTime authTs;

    protected FraudReportId() {
    }

    public FraudReportId(String cardNum, LocalDateTime authTs) {
        this.cardNum = cardNum;
        this.authTs = authTs;
    }

    public String getCardNum() {
        return cardNum;
    }

    public LocalDateTime getAuthTs() {
        return authTs;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FraudReportId that)) {
            return false;
        }
        return Objects.equals(cardNum, that.cardNum) && Objects.equals(authTs, that.authTs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cardNum, authTs);
    }
}
