package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The IMS child key of PAUTDTL1 qualified by its root: the account id of the parent segment plus
 * PA-AUTHORIZATION-KEY, the fourteen character concatenation of PA-AUTH-DATE-9C and
 * PA-AUTH-TIME-9C that COPAUS0C moves into CDEMO-CPVS-PAU-SELECTED and COPAUS1C reads back.
 *
 * <p>Keeping the key as the same fourteen characters the maps and the COMMAREA carry means the
 * selection value the UI sends is byte for byte the value the source transaction used, so the
 * complemented date and time ordering of the IMS database is preserved by an ascending sort.
 */
@Embeddable
public class AuthorizationDetailId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "acct_id", nullable = false)
    private Long acctId;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "auth_key", length = 14, nullable = false, columnDefinition = "char(14)")
    private String authKey;

    protected AuthorizationDetailId() {
    }

    public AuthorizationDetailId(Long acctId, String authKey) {
        this.acctId = acctId;
        this.authKey = authKey;
    }

    public Long getAcctId() {
        return acctId;
    }

    public String getAuthKey() {
        return authKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthorizationDetailId that)) {
            return false;
        }
        return Objects.equals(acctId, that.acctId) && Objects.equals(authKey, that.authKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(acctId, authKey);
    }

    @Override
    public String toString() {
        return acctId + "/" + authKey;
    }
}
