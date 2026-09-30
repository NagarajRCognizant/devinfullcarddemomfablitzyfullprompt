package com.carddemo.api;

import com.carddemo.api.dto.StatementPartyResponse;
import com.carddemo.service.StatementPartyService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The read the statement job performs in place of its own CARDXREF, CUSTDAT and ACCTDAT reads.
 *
 * <p>Like the cardholder lookup this is a service to service capability rather than a screen, so
 * it carries no menu authorisation of its own and the gateway does not expose it to browsers.
 */
@RestController
@RequestMapping(path = "/api/statement-parties", produces = MediaType.APPLICATION_JSON_VALUE)
public class StatementPartyController {

    private static final int MAX_PAGE_SIZE = 500;

    private final StatementPartyService statementPartyService;

    public StatementPartyController(StatementPartyService statementPartyService) {
        this.statementPartyService = statementPartyService;
    }

    @GetMapping
    public StatementPartyResponse page(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "100") int size) {
        return statementPartyService.page(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));
    }
}
