package com.carddemo.transaction.api;

import com.carddemo.security.SignedOnUser;
import com.carddemo.transaction.api.dto.ReportRequestForm;
import com.carddemo.transaction.api.dto.ReportRequestResponse;
import com.carddemo.transaction.service.ReportRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The CR00 screen as a REST resource. */
@RestController
@RequestMapping("/api/transaction-reports")
@Tag(name = "Transaction reports", description = "CORPT00C, the request that submitted TRANREPT")
public class ReportRequestController {

    private final ReportRequestService reportRequests;

    public ReportRequestController(ReportRequestService reportRequests) {
        this.reportRequests = reportRequests;
    }

    /**
     * PROCESS-ENTER-KEY. The signed-on user replaces the terminal operator the JCL recorded.
     *
     * <p>The name of a token authentication is the realm's own identifier of the user; what CORPT00C
     * recorded is the eight character SEC-USR-ID, which the token carries as its username.
     */
    @PostMapping
    @Operation(summary = "Request a transaction report")
    public ReportRequestResponse submit(@RequestBody ReportRequestForm form,
            Authentication authentication) {
        return reportRequests.submit(form, requestedBy(authentication));
    }

    private static String requestedBy(Authentication authentication) {
        if (authentication == null) {
            return "UNKNOWN";
        }
        return authentication.getPrincipal() instanceof Jwt token
                ? SignedOnUser.of(token).userId()
                : authentication.getName();
    }
}
