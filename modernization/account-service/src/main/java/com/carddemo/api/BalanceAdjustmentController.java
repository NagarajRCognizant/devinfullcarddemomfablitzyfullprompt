package com.carddemo.api;

import com.carddemo.api.dto.BalanceAdjustmentRequest;
import com.carddemo.api.dto.BalanceAdjustmentResponse;
import com.carddemo.service.BalanceAdjustmentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The service to service write the transaction context uses in place of its own rewrite of
 * ACCTDAT.
 *
 * <p>Like the cardholder lookup it is not a screen replacement and carries no menu rule of its
 * own: any authenticated caller with a token for this service may call it, and the gateway does
 * not route it to browsers.
 */
@RestController
@RequestMapping(path = "/api/accounts", produces = MediaType.APPLICATION_JSON_VALUE)
public class BalanceAdjustmentController {

    private final BalanceAdjustmentService balanceAdjustmentService;

    public BalanceAdjustmentController(BalanceAdjustmentService balanceAdjustmentService) {
        this.balanceAdjustmentService = balanceAdjustmentService;
    }

    @PostMapping(path = "/{accountId}/balance-adjustments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public BalanceAdjustmentResponse adjust(@PathVariable long accountId,
                                            @Valid @RequestBody BalanceAdjustmentRequest request) {
        return balanceAdjustmentService.apply(accountId, request);
    }
}
