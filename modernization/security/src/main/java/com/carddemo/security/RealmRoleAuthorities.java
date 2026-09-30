package com.carddemo.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Turns the realm roles of a Keycloak token into the authorities the screens were already written
 * against, so nothing downstream of the sign-on changes.
 *
 * <p>{@code realm_access.roles} holds {@code ADMIN} for SEC-USR-TYPE 'A' and {@code USER} for every
 * other value, and this adds the {@code ROLE_} prefix Spring Security compares on. The flat
 * {@code roles} claim of the token the identity service used to mint itself is still read, so the
 * legacy sign-on of a cutover deployment keeps working while both are in place.
 */
public final class RealmRoleAuthorities implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String REALM_ACCESS = "realm_access";
    private static final String ROLES = "roles";
    private static final String PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt token) {
        Set<String> names = new LinkedHashSet<>();
        for (String role : realmRoles(token)) {
            names.add(role.startsWith(PREFIX) ? role : PREFIX + role);
        }
        List<String> flat = token.getClaimAsStringList(ROLES);
        if (flat != null) {
            for (String role : flat) {
                names.add(role.startsWith(PREFIX) ? role : PREFIX + role);
            }
        }
        Collection<GrantedAuthority> authorities = new ArrayList<>(names.size());
        for (String name : names) {
            authorities.add(new SimpleGrantedAuthority(name));
        }
        return authorities;
    }

    private static List<String> realmRoles(Jwt token) {
        Map<String, Object> realmAccess = token.getClaimAsMap(REALM_ACCESS);
        if (realmAccess == null) {
            return List.of();
        }
        Object roles = realmAccess.get(ROLES);
        if (roles instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
