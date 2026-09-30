package com.carddemo.api;

import com.carddemo.api.dto.SignOnRequest;
import com.carddemo.api.dto.SignOnResponse;
import com.carddemo.service.SignOnService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The sign-on screen of app/cbl/COSGN00C.cbl, as this service performed it before the realm.
 *
 * <p>Only mapped in {@code carddemo.security.mode=legacy}. In the supported mode the screen is the
 * login page of the realm and the browser never posts a password to this service.
 */
@RestController
@RequestMapping("/api/signon")
@ConditionalOnProperty(prefix = "carddemo.security", name = "mode", havingValue = "legacy")
public class SignOnController {

    private final SignOnService signOnService;

    public SignOnController(SignOnService signOnService) {
        this.signOnService = signOnService;
    }

    @PostMapping
    public ResponseEntity<SignOnResponse> signOn(@RequestBody SignOnRequest request) {
        return ResponseEntity.ok(signOnService.signOn(request));
    }
}
