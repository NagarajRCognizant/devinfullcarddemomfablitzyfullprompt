package com.carddemo.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * USRSEC VSAM KSDS record (app/cpy/CSUSR01Y.cpy SEC-USER-DATA, RECLN 80, KEYS(8 0)).
 *
 * <p>Every field keeps the length of its PIC clause and is stored as CHAR, because the programs
 * move fixed length fields in and out of the record and compare them padded with spaces
 * (COUSR02C compares the screen field with SEC-USR-FNAME directly). SEC-USR-FILLER is carried so a
 * row still round trips to the 80 byte record.
 */
@Entity
@Table(name = "security_user")
public class SecurityUserEntity {

    /** SEC-USR-ID PIC X(08), the KSDS key. */
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sec_usr_id", length = 8, nullable = false, columnDefinition = "char(8)")
    private String userId;

    /** SEC-USR-FNAME PIC X(20). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sec_usr_fname", length = 20, nullable = false, columnDefinition = "char(20)")
    private String firstName;

    /** SEC-USR-LNAME PIC X(20). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sec_usr_lname", length = 20, nullable = false, columnDefinition = "char(20)")
    private String lastName;

    /** SEC-USR-PWD PIC X(08), held in clear text by the source file. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sec_usr_pwd", length = 8, nullable = false, columnDefinition = "char(8)")
    private String password;

    /** SEC-USR-TYPE PIC X(01), 'A' for administrator, anything else for a regular user. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sec_usr_type", length = 1, nullable = false, columnDefinition = "char(1)")
    private String userType;

    /** SEC-USR-FILLER PIC X(23), never read or written by the programs. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "sec_usr_filler", length = 23, nullable = false, columnDefinition = "char(23)")
    private String filler = " ".repeat(23);

    /**
     * Id of the realm account this record signs on with; not part of the record layout.
     *
     * <p>Null for a record that has no account yet, which is every record of the shipped file until
     * the reconciliation runs.
     */
    @Column(name = "keycloak_user_id", length = 36)
    private String keycloakUserId;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getKeycloakUserId() {
        return keycloakUserId;
    }

    public void setKeycloakUserId(String keycloakUserId) {
        this.keycloakUserId = keycloakUserId;
    }

    public String getFiller() {
        return filler;
    }

    public void setFiller(String filler) {
        this.filler = filler;
    }
}
