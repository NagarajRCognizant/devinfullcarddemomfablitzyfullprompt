package com.carddemo.api;

import com.carddemo.api.dto.CardholderLookupResponse;
import com.carddemo.service.CardholderLookupService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The service to service read the authorization context uses in place of its own reads of CARDXREF,
 * ACCTDAT and CUSTDAT.
 *
 * <p>It is not a screen replacement, so it carries no menu authorisation check of its own: any
 * authenticated caller with a valid token may read it, and the gateway does not expose it to
 * browsers. This is the anti corruption boundary between the account and authorization contexts.
 */
@RestController
@RequestMapping(path = "/api/cardholders", produces = MediaType.APPLICATION_JSON_VALUE)
public class CardholderLookupController {

    private final CardholderLookupService cardholderLookupService;

    public CardholderLookupController(CardholderLookupService cardholderLookupService) {
        this.cardholderLookupService = cardholderLookupService;
    }

    @GetMapping("/by-card/{cardNumber}")
    public CardholderLookupResponse byCard(@PathVariable String cardNumber) {
        return cardholderLookupService.byCardNumber(cardNumber);
    }

    @GetMapping("/by-account/{accountId}")
    public CardholderLookupResponse byAccount(@PathVariable long accountId) {
        return cardholderLookupService.byAccountId(accountId);
    }
}
