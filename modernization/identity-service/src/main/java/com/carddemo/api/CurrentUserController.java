package com.carddemo.api;

import com.carddemo.api.dto.CurrentUserResponse;
import com.carddemo.service.CurrentUserService;
import com.carddemo.security.SignedOnUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What COSGN00C reported after a successful sign-on, for a caller the realm signed on instead.
 *
 * <p>The SPA has a token but not the USRSEC row behind it, and the routing of PROCESS-ENTER-KEY is a
 * property of that row; the user id is taken from the verified token only, never from the request.
 */
@RestController
@RequestMapping("/api/me")
public class CurrentUserController {

    private final CurrentUserService currentUsers;

    public CurrentUserController(CurrentUserService currentUsers) {
        this.currentUsers = currentUsers;
    }

    @GetMapping
    public ResponseEntity<CurrentUserResponse> me(@AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok(currentUsers.forToken(SignedOnUser.of(token)));
    }
}
