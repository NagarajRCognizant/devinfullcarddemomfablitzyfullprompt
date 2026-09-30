package com.carddemo.api;

import com.carddemo.api.dto.AccountUpdateRequest;
import com.carddemo.api.dto.AccountUpdateResponse;
import com.carddemo.service.AccountUpdateService;
import com.carddemo.service.MenuAccessService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST replacement for CICS transaction CAUP (program COACTUPC).
 *
 * <p>Three operations replace the pseudo conversational turns: fetch corresponds to the first
 * ENTER on the account id, validate to the ENTER that runs the edits and confirm to F5. The state
 * the COMMAREA held travels in the request and response bodies instead, which is what lets the
 * server stay stateless without changing the order of the checks.
 */
@RestController
@RequestMapping(path = "/api/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountUpdateController {

    private final AccountUpdateService accountUpdateService;
    private final MenuAccessService menuAccessService;

    public AccountUpdateController(AccountUpdateService accountUpdateService,
                                   MenuAccessService menuAccessService) {
        this.accountUpdateService = accountUpdateService;
        this.menuAccessService = menuAccessService;
    }

    /**
     * First turn as the map sends it: the filter is passed exactly as keyed, so a blank or '*' value
     * reaches 1210-EDIT-ACCOUNT and gets its own message and flag instead of failing to route.
     */
    @GetMapping("/update")
    public AccountUpdateResponse fetchKeyed(
            @RequestParam(name = "accountId", required = false, defaultValue = "") String accountId,
            Authentication authentication) {
        menuAccessService.requireAccess(SignedOnUser.userType(authentication), "COACTUPC");
        return accountUpdateService.fetch(accountId);
    }

    /** First turn for a filter that has an addressable value. */
    @GetMapping("/{accountId}/update")
    public AccountUpdateResponse fetch(
            @PathVariable String accountId,
            Authentication authentication) {
        menuAccessService.requireAccess(SignedOnUser.userType(authentication), "COACTUPC");
        return accountUpdateService.fetch(accountId);
    }

    /** Second turn: change detection and the field edits. */
    @PostMapping(path = "/{accountId}/update/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AccountUpdateResponse validate(
            @PathVariable String accountId,
            @Valid @RequestBody AccountUpdateRequest request,
            Authentication authentication) {
        menuAccessService.requireAccess(SignedOnUser.userType(authentication), "COACTUPC");
        return accountUpdateService.validate(request);
    }

    /** Third turn (F5): lock, compare with the fetched snapshot and rewrite both records. */
    @PostMapping(path = "/{accountId}/update/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public AccountUpdateResponse confirm(
            @PathVariable String accountId,
            @Valid @RequestBody AccountUpdateRequest request,
            Authentication authentication) {
        menuAccessService.requireAccess(SignedOnUser.userType(authentication), "COACTUPC");
        return accountUpdateService.confirm(request);
    }
}
