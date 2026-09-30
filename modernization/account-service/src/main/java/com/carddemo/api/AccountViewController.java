package com.carddemo.api;

import com.carddemo.api.dto.AccountViewResponse;
import com.carddemo.service.AccountViewService;
import com.carddemo.service.MenuAccessService;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST replacement for CICS transaction CAVW (program COACTVWC).
 *
 * <p>The terminal conversation becomes three operations: the prompt the empty map shows, the ENTER
 * key that carries the filter as keyed, and the inquiry on a known account id. The account id stays
 * a string in both because the edits are defined on the keyed characters.
 */
@RestController
@RequestMapping(path = "/api/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
public class AccountViewController {

    private final AccountViewService accountViewService;
    private final MenuAccessService menuAccessService;

    public AccountViewController(AccountViewService accountViewService, MenuAccessService menuAccessService) {
        this.accountViewService = accountViewService;
        this.menuAccessService = menuAccessService;
    }

    /** The first turn of the transaction, which only carries WS-PROMPT-FOR-INPUT. */
    @GetMapping("/view")
    public AccountViewResponse prompt(
            Authentication authentication) {
        menuAccessService.requireAccess(SignedOnUser.userType(authentication), "COACTVWC");
        return accountViewService.prompt();
    }

    /**
     * The ENTER key of the map: the filter is passed exactly as keyed, so blank, spaces and the '*'
     * the map itself writes back all reach 2210-EDIT-ACCOUNT and get its own message and flag.
     */
    @GetMapping("/search")
    public AccountViewResponse search(
            @RequestParam(name = "accountId", required = false, defaultValue = "") String accountId,
            Authentication authentication) {
        menuAccessService.requireAccess(SignedOnUser.userType(authentication), "COACTVWC");
        return accountViewService.view(accountId);
    }

    /** 2210-EDIT-ACCOUNT plus 9000-READ-ACCT for a filter that has an addressable value. */
    @GetMapping("/{accountId}")
    public AccountViewResponse view(
            @PathVariable String accountId,
            Authentication authentication) {
        menuAccessService.requireAccess(SignedOnUser.userType(authentication), "COACTVWC");
        return accountViewService.view(accountId);
    }
}
