package com.carddemo.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What a service accepts as proof that the caller signed on.
 *
 * <p>COSGN00C read USRSEC and decided by itself; every service now defers that decision to Keycloak
 * and only checks the resulting token: it was signed by the realm, it was minted for this service,
 * and a second factor was used to obtain it.
 *
 * @param issuerUri the realm the token must come from, for example
 *     {@code http://localhost:8088/realms/carddemo}; the JWKS is discovered from it, so no key is
 *     configured anywhere
 * @param jwkSetUri where to fetch the signing keys when that is not where the tokens say the realm is:
 *     a browser reaches Keycloak on a published address and a service reaches it on an internal one,
 *     and the {@code iss} claim can only hold one of the two. Left unset, the keys are discovered from
 *     {@code issuerUri}, which is what a deployment with a single address does
 * @param audience the audience of this service, which the realm adds with an audience mapper; a
 *     token minted for another service is refused even though the signature is good
 * @param clockSkew seconds of tolerance on {@code exp} and {@code nbf}
 * @param requireSecondFactor whether a user token must carry evidence of MFA; only a deployment
 *     that has to accept single factor tokens during a cutover turns this off
 * @param browserClientIds the {@code azp} values whose tokens can only have been obtained through
 *     the realm browser flow, which always asks for the TOTP; direct grants are disabled on those
 *     clients, so the client id is the MFA evidence
 * @param serviceClientIds the {@code azp} values of client credentials callers: the converted CP00
 *     trigger has no signed-on user and therefore no second factor
 * @param amrValues authentication method references that count as a second factor when the realm
 *     populates {@code amr}
 * @param acrValues authentication context class references that count as a second factor when the
 *     realm maps a level of authentication onto {@code acr}
 */
@ConfigurationProperties(prefix = "carddemo.security.token")
public record TokenProperties(
        String issuerUri,
        String jwkSetUri,
        String audience,
        Long clockSkew,
        Boolean requireSecondFactor,
        List<String> browserClientIds,
        List<String> serviceClientIds,
        List<String> amrValues,
        List<String> acrValues) {

    public TokenProperties {
        clockSkew = clockSkew == null ? 60L : clockSkew;
        requireSecondFactor = requireSecondFactor == null || requireSecondFactor;
        browserClientIds = browserClientIds == null ? List.of("carddemo-ui") : browserClientIds;
        serviceClientIds = serviceClientIds == null
                ? List.of("carddemo-authorization-svc")
                : serviceClientIds;
        amrValues = amrValues == null ? List.of("otp", "mfa") : amrValues;
        acrValues = acrValues == null ? List.of() : acrValues;
    }
}
