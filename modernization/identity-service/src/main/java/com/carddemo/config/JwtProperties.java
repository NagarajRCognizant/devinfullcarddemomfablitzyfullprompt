package com.carddemo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Signing configuration of the tokens that replace the CICS COMMAREA fields CDEMO-USER-ID and
 * CDEMO-USER-TYPE.
 *
 * @param secret HMAC secret shared with the services that validate the token
 * @param issuer issuer claim written into and expected in the token
 * @param ttlMinutes lifetime of a token; the legacy pseudo conversation had no equivalent, so this
 *     is a target platform decision recorded in the mainframe-to-target mapping
 */
@ConfigurationProperties(prefix = "carddemo.security.jwt")
public record JwtProperties(String secret, String issuer, long ttlMinutes) {
}
