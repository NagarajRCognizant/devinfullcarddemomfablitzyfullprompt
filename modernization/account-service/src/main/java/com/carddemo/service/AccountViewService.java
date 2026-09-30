package com.carddemo.service;

import com.carddemo.api.dto.AccountViewResponse;
import com.carddemo.domain.ScreenMessages;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of COACTVWC, the Account View transaction (CAVW).
 *
 * <p>The COBOL program is pseudo conversational: the first turn sends an empty map with a prompt,
 * the next turn edits the keyed account id, performs 9000-READ-ACCT and sends the populated map.
 * The target exposes the second turn as one inquiry operation, so terminal state, the COMMAREA and
 * the PF key handling disappear while the edit order, the read order and the messages do not.
 */
@Service
public class AccountViewService {

    private final AccountReadService accountReadService;
    private final AccountScreenMapper accountScreenMapper;

    public AccountViewService(AccountReadService accountReadService, AccountScreenMapper accountScreenMapper) {
        this.accountReadService = accountReadService;
        this.accountScreenMapper = accountScreenMapper;
    }

    /** 2210-EDIT-ACCOUNT followed by 9000-READ-ACCT and SEND-MAP with WS-INFORM-OUTPUT. */
    @Transactional(readOnly = true)
    public AccountViewResponse view(String keyedAccountId) {
        long accountId = accountReadService.editAccountId(keyedAccountId, false);
        AccountRecords records = accountReadService.readAccount(accountId);
        return new AccountViewResponse(
                accountScreenMapper.toDetails(records.account(), records.customer(),
                        records.xref().getXrefCardNum()),
                ScreenMessages.VIEW_INFORM_OUTPUT);
    }

    /** The empty first turn of the transaction, which only shows WS-PROMPT-FOR-INPUT. */
    public AccountViewResponse prompt() {
        return new AccountViewResponse(null, ScreenMessages.VIEW_PROMPT_FOR_INPUT);
    }
}
