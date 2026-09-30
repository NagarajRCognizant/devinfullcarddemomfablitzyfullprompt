package com.carddemo.api.dto;

/**
 * What the COMMAREA carried out of COSGN00C, for a caller the realm has already signed on.
 *
 * <p>Same fields as {@link SignOnResponse} without the token: the SPA holds the token of the realm,
 * so this reports only the USRSEC row and the routing decision of PROCESS-ENTER-KEY.
 */
public record CurrentUserResponse(String userId, String firstName, String lastName, String userType,
        String nextScreen) {
}
