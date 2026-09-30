package com.carddemo.security;

import java.util.List;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Refuses a user token that was not obtained with a second factor.
 *
 * <p>Three things can satisfy the check, in this order:
 *
 * <ol>
 *   <li>the token belongs to a client credentials caller - the converted CP00 trigger runs without a
 *       signed-on user, exactly as the CICS trigger monitor did;
 *   <li>the realm populated {@code amr} or {@code acr} with a configured value;
 *   <li>the token was issued to a browser client, whose only enabled flow is the authorization code
 *       flow, and whose browser flow always asks for the TOTP - a token with that {@code azp} cannot
 *       exist without the second factor.
 * </ol>
 *
 * <p>The third rule carries the guarantee in this realm, because Keycloak leaves {@code amr} empty
 * for a plain password plus OTP login; it is a statement about the realm, so
 * {@code carddemo.security.token.browser-client-ids} has to name only clients that have the direct
 * access grant disabled.
 */
public final class SecondFactorValidator implements OAuth2TokenValidator<Jwt> {

    private static final String AUTHORIZED_PARTY = "azp";
    private static final String METHODS = "amr";
    private static final String CONTEXT_CLASS = "acr";

    private final TokenProperties properties;

    public SecondFactorValidator(TokenProperties properties) {
        this.properties = properties;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (!properties.requireSecondFactor()) {
            return OAuth2TokenValidatorResult.success();
        }
        String authorizedParty = token.getClaimAsString(AUTHORIZED_PARTY);
        if (authorizedParty != null && properties.serviceClientIds().contains(authorizedParty)) {
            return OAuth2TokenValidatorResult.success();
        }
        List<String> methods = token.getClaimAsStringList(METHODS);
        if (methods != null && methods.stream().anyMatch(properties.amrValues()::contains)) {
            return OAuth2TokenValidatorResult.success();
        }
        String contextClass = token.getClaimAsString(CONTEXT_CLASS);
        if (contextClass != null && properties.acrValues().contains(contextClass)) {
            return OAuth2TokenValidatorResult.success();
        }
        if (authorizedParty != null && properties.browserClientIds().contains(authorizedParty)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_TOKEN,
                "The token carries no evidence of a second factor",
                null));
    }
}
