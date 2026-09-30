package com.carddemo.api;

import com.carddemo.api.dto.MenuResponse;
import com.carddemo.service.MenuAccessService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The main menu of app/cbl/COMEN01C.cbl, restricted to the two options this service owns.
 *
 * <p>COMEN01C shows the option list and refuses an admin-only option to a regular user, using the
 * CDEMO-USER-TYPE value that COSGN00C put in the COMMAREA. That value now arrives in the token the
 * identity service issued, so the caller no longer states its own user type.
 */
@RestController
public class MenuController {

    private final MenuAccessService menuAccessService;

    public MenuController(MenuAccessService menuAccessService) {
        this.menuAccessService = menuAccessService;
    }

    @GetMapping(path = "/api/menu", produces = MediaType.APPLICATION_JSON_VALUE)
    public MenuResponse menu(Authentication authentication) {
        return menuAccessService.describe(SignedOnUser.userId(authentication),
                SignedOnUser.userType(authentication));
    }
}
