package com.carddemo.keycloak;

import java.util.Optional;

/**
 * The credential store the sign-on now uses, which USRSEC no longer is.
 *
 * <p>COUSR01C, COUSR02C and COUSR03C wrote the password into the USRSEC record itself. The record is
 * still written, because the business context keys off SEC-USR-ID and reports SEC-USR-TYPE, but the
 * credential and the second factor belong to the realm, so each of the three programs now has a
 * second write to perform. This interface is that second write, so the programs' service keeps
 * reading like the source and the compensation is in one place.
 */
public interface UserDirectory {

    /**
     * Creates the account the user signs on with and returns its immutable realm id.
     *
     * <p>The account is created with the initial password of the screen marked temporary and with the
     * TOTP enrolment required, so the first sign-on enrols the authenticator; nothing in CardDemo can
     * enrol it on the user's behalf.
     */
    String create(DirectoryUser user);

    /** Applies the changed name, type and password of COUSR02C to the existing account. */
    void update(String directoryUserId, DirectoryUser user);

    /** DELETE-USER-INFO of COUSR03C: the account goes with the record. */
    void delete(String directoryUserId);

    /** The realm id of an account created before the record carried it, for reconciliation. */
    Optional<String> findId(String userId);
}
