package com.carddemo.api.dto;

/**
 * The outcome of PROCESS-ENTER-KEY of COSGN00C when the password matches.
 *
 * <p>The program transfers control to COADM01C for an administrator and to COMEN01C for anyone
 * else; the target is reported as {@code nextScreen} so the UI performs the same routing. The token
 * replaces the CICS COMMAREA that carried CDEMO-USER-ID and CDEMO-USER-TYPE between screens.
 */
public record SignOnResponse(String userId, String firstName, String lastName, String userType,
        String nextScreen, String accessToken, long expiresInSeconds) {
}
