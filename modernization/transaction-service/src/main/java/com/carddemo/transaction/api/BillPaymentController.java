package com.carddemo.transaction.api;

import com.carddemo.transaction.api.dto.BillPaymentRequest;
import com.carddemo.transaction.api.dto.BillPaymentResponse;
import com.carddemo.transaction.service.BillPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The CB00 screen as a REST resource. */
@RestController
@RequestMapping("/api/bill-payments")
@Tag(name = "Bill payment", description = "COBIL00C, the online payment of the full balance")
public class BillPaymentController {

    private final BillPaymentService billPayments;

    public BillPaymentController(BillPaymentService billPayments) {
        this.billPayments = billPayments;
    }

    /** The balance enquiry the screen makes before it asks for a confirmation. */
    @GetMapping("/{accountId}")
    @Operation(summary = "The balance that would be paid")
    public BillPaymentResponse balance(@PathVariable String accountId) {
        return billPayments.balance(accountId);
    }

    /** The confirmed payment. The request carries the key that makes a resubmission safe. */
    @PostMapping
    @Operation(summary = "Pay the current balance")
    public BillPaymentResponse pay(@RequestBody BillPaymentRequest request) {
        return billPayments.pay(request);
    }
}
