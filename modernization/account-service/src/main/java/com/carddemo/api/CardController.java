package com.carddemo.api;

import com.carddemo.api.dto.CardDetailResponse;
import com.carddemo.api.dto.CardListResponse;
import com.carddemo.api.dto.CardUpdateRequest;
import com.carddemo.api.dto.CardUpdateResponse;
import com.carddemo.service.CardDetailService;
import com.carddemo.service.CardListService;
import com.carddemo.service.CardUpdateService;
import com.carddemo.service.MenuAccessService;
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
 * REST replacement for CICS transactions CCLI (COCRDLIC), CCDL (COCRDSLC) and CCUP (COCRDUPC).
 *
 * <p>Each terminal turn becomes one request. The list carries the page keys the COMMAREA held so
 * that PF7 and PF8 restart the browse where it stopped, and the update is split into the edit-only
 * turn and the PF5 turn the source distinguishes in 2000-DECIDE-ACTION.
 */
@RestController
@RequestMapping(path = "/api/cards", produces = MediaType.APPLICATION_JSON_VALUE)
public class CardController {

    private final CardListService cardListService;
    private final CardDetailService cardDetailService;
    private final CardUpdateService cardUpdateService;
    private final MenuAccessService menuAccessService;

    public CardController(CardListService cardListService, CardDetailService cardDetailService,
                          CardUpdateService cardUpdateService, MenuAccessService menuAccessService) {
        this.cardListService = cardListService;
        this.cardDetailService = cardDetailService;
        this.cardUpdateService = cardUpdateService;
        this.menuAccessService = menuAccessService;
    }

    /** One page of COCRDLIC, including the ENTER, PF7 and PF8 turns. */
    @GetMapping
    public CardListResponse list(
            @RequestParam(name = "accountId", required = false, defaultValue = "") String accountId,
            @RequestParam(name = "cardNumber", required = false, defaultValue = "") String cardNumber,
            @RequestParam(name = "direction", required = false, defaultValue = "FIRST") String direction,
            @RequestParam(name = "firstCardNumber", required = false) String firstCardNumber,
            @RequestParam(name = "lastCardNumber", required = false) String lastCardNumber,
            @RequestParam(name = "pageNumber", required = false, defaultValue = "1") int pageNumber,
            Authentication authentication) {
        menuAccessService.requireAccess(SignedOnUser.userType(authentication), "COCRDLIC");
        return cardListService.list(accountId, cardNumber, direction, firstCardNumber, lastCardNumber,
                pageNumber);
    }

    /** COCRDSLC, the view screen. */
    @GetMapping("/{cardNumber}")
    public CardDetailResponse view(
            @PathVariable String cardNumber,
            @RequestParam(name = "accountId", required = false, defaultValue = "") String accountId,
            Authentication authentication) {
        menuAccessService.requireAccess(SignedOnUser.userType(authentication), "COCRDSLC");
        return cardDetailService.view(accountId, cardNumber);
    }

    /** The first turn of COCRDUPC, which fills the map with the record to be changed. */
    @GetMapping("/{cardNumber}/update")
    public CardDetailResponse fetchForUpdate(
            @PathVariable String cardNumber,
            @RequestParam(name = "accountId", required = false, defaultValue = "") String accountId,
            Authentication authentication) {
        menuAccessService.requireAccess(SignedOnUser.userType(authentication), "COCRDUPC");
        return cardUpdateService.fetch(accountId, cardNumber);
    }

    /** The ENTER turn of COCRDUPC: the changes are edited and the PF5 confirmation is requested. */
    @PostMapping(path = "/validate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CardUpdateResponse validate(@RequestBody CardUpdateRequest request,
                                       Authentication authentication) {
        menuAccessService.requireAccess(SignedOnUser.userType(authentication), "COCRDUPC");
        return cardUpdateService.validate(request);
    }

    /** The PF5 turn of COCRDUPC, which rewrites the record. */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public CardUpdateResponse save(@RequestBody CardUpdateRequest request,
                                   Authentication authentication) {
        menuAccessService.requireAccess(SignedOnUser.userType(authentication), "COCRDUPC");
        return cardUpdateService.save(request);
    }
}
