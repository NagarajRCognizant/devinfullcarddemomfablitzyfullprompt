package com.carddemo.service;

import com.carddemo.cobol.CobolText;
import com.carddemo.domain.ScreenMessages;
import com.carddemo.domain.validation.FieldFlag;
import com.carddemo.domain.validation.ScreenField;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.entity.AccountEntity;
import com.carddemo.persistence.entity.CardXrefEntity;
import com.carddemo.persistence.entity.CustomerEntity;
import com.carddemo.persistence.repository.AccountRepository;
import com.carddemo.persistence.repository.CardXrefRepository;
import com.carddemo.persistence.repository.CustomerRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The account key edit and the three keyed reads shared by both online programs.
 *
 * <p>Sequence and stop conditions come from paragraph 9000-READ-ACCT: the cross reference is read
 * through the account alternate index path (CXACAIX), and only if it is found does the account
 * master read run, and only if that succeeds does the customer read run with the customer id taken
 * from the cross reference record. Each read distinguishes found, not found and any other status,
 * exactly as the {@code EVALUATE WS-RESP-CD} blocks in paragraphs 9200, 9300 and 9400 do; a
 * missing record is an input error with its own message rather than a failure, and it flags the
 * filter that produced the miss the way those paragraphs set WS-EDIT-ACCT-FLAG/WS-EDIT-CUST-FLAG.
 */
@Service
public class AccountReadService {

    private final CardXrefRepository cardXrefRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountReadService(CardXrefRepository cardXrefRepository, AccountRepository accountRepository,
                              CustomerRepository customerRepository) {
        this.cardXrefRepository = cardXrefRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * 2210-EDIT-ACCOUNT of COACTVWC and 1210-EDIT-ACCOUNT of COACTUPC.
     *
     * <p>Both paragraphs treat an absent value and a non numeric or zero value as separate errors,
     * but each program words the second message differently, so the caller supplies its own.
     *
     * @return the eleven digit account id
     */
    public long editAccountId(String keyed, boolean updateProgram) {
        String value = CobolText.normaliseAsterisk(keyed);
        if (CobolText.isBlank(value)) {
            throw new ScreenValidationException("ACCOUNT_ID_BLANK", ScreenMessages.PROMPT_FOR_ACCT,
                    Map.of(ScreenField.ACCOUNT_ID, FieldFlag.BLANK));
        }
        String trimmed = CobolText.trim(value);
        String message = updateProgram
                ? ScreenMessages.UPDATE_ACCT_FILTER_NOT_VALID
                : ScreenMessages.VIEW_ACCT_FILTER_NOT_VALID;
        // The map field is PIC X(11) and the COBOL applies IS NOT NUMERIC to all eleven
        // positions, so a value shorter than eleven digits fails the edit on the terminal too.
        if (trimmed.length() != 11 || !CobolText.isNumeric(trimmed)) {
            throw new ScreenValidationException("ACCOUNT_ID_NOT_VALID", message,
                    Map.of(ScreenField.ACCOUNT_ID, FieldFlag.NOT_OK));
        }
        long accountId = Long.parseLong(trimmed);
        if (accountId == 0L) {
            throw new ScreenValidationException("ACCOUNT_ID_NOT_VALID", message,
                    Map.of(ScreenField.ACCOUNT_ID, FieldFlag.NOT_OK));
        }
        return accountId;
    }

    /** 9000-READ-ACCT: cross reference, then account master, then customer master. */
    @Transactional(readOnly = true)
    public AccountRecords readAccount(long accountId) {
        CardXrefEntity xref = cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc(accountId)
                .orElseThrow(() -> new RecordNotFoundException(
                        ScreenMessages.accountNotInCrossReference(Long.toString(accountId)),
                        Map.of(ScreenField.ACCOUNT_ID, FieldFlag.NOT_OK)));
        AccountEntity account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RecordNotFoundException(
                        ScreenMessages.accountNotInMaster(Long.toString(accountId)),
                        Map.of(ScreenField.ACCOUNT_ID, FieldFlag.NOT_OK)));
        CustomerEntity customer = customerRepository.findById(xref.getXrefCustId())
                .orElseThrow(() -> new RecordNotFoundException(
                        ScreenMessages.customerNotInMaster(Long.toString(xref.getXrefCustId())),
                        Map.of(ScreenField.CUSTOMER_ID, FieldFlag.NOT_OK)));
        return new AccountRecords(xref, account, customer);
    }
}
