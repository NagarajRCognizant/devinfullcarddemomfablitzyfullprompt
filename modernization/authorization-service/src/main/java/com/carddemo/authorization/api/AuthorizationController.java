package com.carddemo.authorization.api;

import com.carddemo.authorization.api.dto.AuthorizationDetailResponse;
import com.carddemo.authorization.api.dto.AuthorizationSummaryResponse;
import com.carddemo.authorization.domain.AuthorizationMessages;
import com.carddemo.authorization.service.AuthorizationInquiryService;
import com.carddemo.authorization.service.FraudMarkingService;
import com.carddemo.exception.ScreenValidationException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The REST contract that replaces transactions CPVS and CPVD.
 *
 * <p>The three 3270 actions become three resources: a page of the summary list, one authorization,
 * and a state change on that authorization. PF7 and PF8 are gone - paging is a query parameter
 * carrying the key of the last row seen - and PF5 becomes an explicit POST, which is the
 * modernization Section 8.4.5 requires while the underlying reads, ordering and messages stay as
 * the programs produced them.
 */
@RestController
@RequestMapping(path = "/api/authorizations", produces = MediaType.APPLICATION_JSON_VALUE)
public class AuthorizationController {

    private final AuthorizationInquiryService inquiryService;
    private final FraudMarkingService fraudMarkingService;

    public AuthorizationController(AuthorizationInquiryService inquiryService,
                                   FraudMarkingService fraudMarkingService) {
        this.inquiryService = inquiryService;
        this.fraudMarkingService = fraudMarkingService;
    }

    /**
     * Map COPAU0A. The account id arrives as text so the two edits of PROCESS-ENTER-KEY keep their
     * own messages: an empty field and a non numeric field were distinct outcomes on the screen.
     */
    @GetMapping
    public AuthorizationSummaryResponse summary(
            @RequestParam(name = "accountId", required = false) String accountId,
            @RequestParam(name = "afterAuthKey", required = false) String afterAuthKey) {
        return inquiryService.summary(requireNumericAccountId(accountId), blankToNull(afterAuthKey));
    }

    /**
     * Map COPAU1A for the selected authorization, with the key of the next one.
     *
     * <p>{@code selection} is optional and carries the character a client puts in the selection
     * column of the list. The source accepted only S or s and redisplayed the list otherwise, so
     * the rule is enforced here as well as in the UI rather than living only in the browser.
     */
    @GetMapping("/{accountId}/{authKey}")
    public AuthorizationDetailResponse detail(@PathVariable long accountId,
                                              @PathVariable String authKey,
                                              @RequestParam(name = "selection", required = false)
                                              String selection) {
        requireValidSelection(selection);
        return inquiryService.detail(accountId, authKey);
    }

    /** PF5 of CPVD: confirmed becomes removed, anything else becomes confirmed. */
    @PostMapping(path = "/{accountId}/{authKey}/fraud")
    public AuthorizationDetailResponse toggleFraud(@PathVariable long accountId,
                                                   @PathVariable String authKey) {
        return fraudMarkingService.toggleFraud(accountId, authKey);
    }

    /** PROCESS-ENTER-KEY of COPAUS0C: the field is required and must be numeric. */
    private static long requireNumericAccountId(String accountId) {
        String value = accountId == null ? "" : accountId.trim();
        if (value.isEmpty()) {
            throw new ScreenValidationException("MISSING_ACCT_ID",
                    AuthorizationMessages.PLEASE_ENTER_ACCT_ID, Map.of());
        }
        if (!value.chars().allMatch(Character::isDigit)) {
            throw new ScreenValidationException("INVALID_ACCT_ID",
                    AuthorizationMessages.ACCT_ID_MUST_BE_NUMERIC, Map.of());
        }
        return Long.parseLong(value);
    }

    /** PROCESS-ENTER-KEY of COPAUS0C: the selection character must be S or s. */
    private static void requireValidSelection(String selection) {
        String value = blankToNull(selection);
        if (value != null && !"S".equalsIgnoreCase(value)) {
            throw new ScreenValidationException("INVALID_SELECTION",
                    AuthorizationMessages.INVALID_SELECTION, Map.of());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
