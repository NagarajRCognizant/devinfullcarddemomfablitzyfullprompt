package com.carddemo.transaction.api;

import com.carddemo.transaction.api.dto.TransactionAddRequest;
import com.carddemo.transaction.api.dto.TransactionAddResponse;
import com.carddemo.transaction.api.dto.TransactionDetailResponse;
import com.carddemo.transaction.api.dto.TransactionListResponse;
import com.carddemo.transaction.service.TransactionAddService;
import com.carddemo.transaction.service.TransactionListService;
import com.carddemo.transaction.service.TransactionViewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The CT00, CT01 and CT02 screens as REST resources. */
@RestController
@RequestMapping("/api/transactions")
@Tag(name = "Transactions", description = "COTRN00C list, COTRN01C view and COTRN02C add")
public class TransactionController {

    private final TransactionListService listService;
    private final TransactionViewService viewService;
    private final TransactionAddService addService;

    public TransactionController(TransactionListService listService,
                                 TransactionViewService viewService,
                                 TransactionAddService addService) {
        this.listService = listService;
        this.viewService = viewService;
        this.addService = addService;
    }

    /**
     * COTRN00C. The direction and the two boundary identifiers replace the PF7 and PF8 keys and
     * the browse position the commarea carried between turns.
     */
    @GetMapping
    @Operation(summary = "One screenful of the transaction list")
    public TransactionListResponse list(
            @RequestParam(required = false) String tranId,
            @RequestParam(defaultValue = "FIRST") String direction,
            @RequestParam(required = false) String firstTranId,
            @RequestParam(required = false) String lastTranId,
            @RequestParam(defaultValue = "1") int pageNumber) {
        return switch (direction.toUpperCase()) {
            case "NEXT" -> listService.next(lastTranId, pageNumber);
            case "PREVIOUS" -> listService.previous(firstTranId, pageNumber);
            default -> listService.first(tranId);
        };
    }

    /** The selection column of COTRN00C: only 'S' opens the detail screen. */
    @GetMapping("/{tranId}")
    @Operation(summary = "One transaction, as the view screen shows it")
    public TransactionDetailResponse view(@PathVariable String tranId,
            @RequestParam(required = false) String selection) {
        String key = selection == null ? tranId : listService.selected(selection, tranId);
        return viewService.view(key);
    }

    /** COTRN02C: the two-step add, driven by the confirmation field of the request. */
    @PostMapping
    @Operation(summary = "Add a transaction")
    public ResponseEntity<TransactionAddResponse> add(@RequestBody TransactionAddRequest request) {
        TransactionAddResponse response = addService.add(request);
        return response.tranId() == null
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(201).body(response);
    }

    /** The PF5 key of COTRN02C, which copies the last transaction into the map. */
    @GetMapping("/last")
    @Operation(summary = "The last transaction on file, prefilled into the add screen")
    public TransactionAddRequest copyLast(@RequestParam(required = false) String accountId,
            @RequestParam(required = false) String cardNumber) {
        return addService.copyLast(accountId, cardNumber);
    }
}
