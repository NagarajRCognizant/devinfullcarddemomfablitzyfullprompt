package com.carddemo.service;

import com.carddemo.api.dto.AccountUpdateForm;
import com.carddemo.api.dto.AccountUpdateRequest;
import com.carddemo.api.dto.AccountUpdateResponse;
import com.carddemo.api.dto.AccountUpdateStatus;
import com.carddemo.domain.ScreenMessages;
import com.carddemo.domain.validation.EditContext;
import com.carddemo.exception.UpdateFailedException;
import com.carddemo.persistence.entity.AccountEntity;
import com.carddemo.persistence.entity.CustomerEntity;
import com.carddemo.persistence.repository.AccountRepository;
import com.carddemo.persistence.repository.CustomerRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of COACTUPC, the Account Update transaction (CAUP).
 *
 * <p>The COBOL program keeps its position in the edit / confirm / rewrite conversation in the
 * ACUP-CHANGE-ACTION byte of the COMMAREA and re-enters 2000-DECIDE-ACTION on every terminal
 * interaction. The target replaces that byte with three explicit operations - fetch, validate and
 * confirm - and returns the state to the client, so no screen state is held on the server. The
 * order in which the source validates, detects "no changes", locks, compares and rewrites is
 * unchanged.
 */
@Service
public class AccountUpdateService {

    private final AccountReadService accountReadService;
    private final AccountScreenMapper accountScreenMapper;
    private final AccountChangeDetector accountChangeDetector;
    private final AccountUpdateValidator accountUpdateValidator;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public AccountUpdateService(AccountReadService accountReadService,
                                AccountScreenMapper accountScreenMapper,
                                AccountChangeDetector accountChangeDetector,
                                AccountUpdateValidator accountUpdateValidator,
                                AccountRepository accountRepository,
                                CustomerRepository customerRepository) {
        this.accountReadService = accountReadService;
        this.accountScreenMapper = accountScreenMapper;
        this.accountChangeDetector = accountChangeDetector;
        this.accountUpdateValidator = accountUpdateValidator;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    /**
     * First turn: 1210-EDIT-ACCOUNT then 9000-READ-ACCT, leaving the program in ACUP-SHOW-DETAILS
     * with the fetched values in both the old and the new areas of the COMMAREA.
     */
    @Transactional(readOnly = true)
    public AccountUpdateResponse fetch(String keyedAccountId) {
        long accountId = accountReadService.editAccountId(keyedAccountId, true);
        AccountRecords records = accountReadService.readAccount(accountId);
        AccountUpdateForm form = accountScreenMapper.toForm(records.account(), records.customer());
        return new AccountUpdateResponse(AccountUpdateStatus.DETAILS_FETCHED,
                ScreenMessages.PROMPT_FOR_CHANGES, null, form, form,
                accountScreenMapper.toDetails(records.account(), records.customer(),
                        records.xref().getXrefCardNum()),
                Map.of());
    }

    /**
     * Second turn: 1205-COMPARE-OLD-NEW and, when something changed, 1200-EDIT-MAP-INPUTS. A
     * submission that changes nothing never reaches the edits, which is why "no change detected"
     * is reported instead of any field message.
     */
    @Transactional(readOnly = true)
    public AccountUpdateResponse validate(AccountUpdateRequest request) {
        long accountId = accountReadService.editAccountId(request.updated().accountId(), true);
        AccountRecords records = accountReadService.readAccount(accountId);

        if (!accountChangeDetector.hasChanges(request.original(), request.updated())) {
            return new AccountUpdateResponse(AccountUpdateStatus.NO_CHANGES,
                    ScreenMessages.PROMPT_FOR_CHANGES, ScreenMessages.NO_CHANGES_DETECTED,
                    request.original(), request.updated(), null, Map.of());
        }

        EditContext ctx = new EditContext();
        ValidatedAccountUpdate validated = accountUpdateValidator.validate(ctx, accountId,
                records.customer().getCustId(), request.updated());
        if (validated == null) {
            return new AccountUpdateResponse(AccountUpdateStatus.VALIDATION_ERROR,
                    ScreenMessages.PROMPT_FOR_CHANGES, ctx.getReturnMessage(),
                    request.original(), request.updated(), null, ctx.getFailedFields());
        }
        return new AccountUpdateResponse(AccountUpdateStatus.CHANGES_VALIDATED,
                ScreenMessages.PROMPT_FOR_CONFIRMATION, null,
                request.original(), request.updated(), null, Map.of());
    }

    /**
     * Third turn: the F5 branch of 2000-DECIDE-ACTION, which performs 9600-WRITE-PROCESSING.
     *
     * <p>The source only reaches the rewrite from ACUP-CHANGES-OK-NOT-CONFIRMED, so the edits and
     * the change detection are re-run here before anything is written: the client cannot confirm a
     * submission that would not have passed the previous turn. Locking both records before the
     * comparison, comparing the stored values with the fetched snapshot and rewriting the account
     * before the customer all follow 9600 and 9700; the CICS SYNCPOINT ROLLBACK that the source
     * issues when the customer rewrite fails is the rollback of this transaction.
     */
    @Transactional
    public AccountUpdateResponse confirm(AccountUpdateRequest request) {
        long accountId = accountReadService.editAccountId(request.updated().accountId(), true);
        AccountRecords records = accountReadService.readAccount(accountId);

        if (!accountChangeDetector.hasChanges(request.original(), request.updated())) {
            return new AccountUpdateResponse(AccountUpdateStatus.NO_CHANGES,
                    ScreenMessages.PROMPT_FOR_CHANGES, ScreenMessages.NO_CHANGES_DETECTED,
                    request.original(), request.updated(), null, Map.of());
        }

        EditContext ctx = new EditContext();
        ValidatedAccountUpdate validated = accountUpdateValidator.validate(ctx, accountId,
                records.customer().getCustId(), request.updated());
        if (validated == null) {
            return new AccountUpdateResponse(AccountUpdateStatus.VALIDATION_ERROR,
                    ScreenMessages.PROMPT_FOR_CHANGES, ctx.getReturnMessage(),
                    request.original(), request.updated(), null, ctx.getFailedFields());
        }

        Optional<AccountEntity> lockedAccount = accountRepository.lockByAcctId(accountId);
        if (lockedAccount.isEmpty()) {
            return failure(AccountUpdateStatus.LOCK_ERROR,
                    ScreenMessages.COULD_NOT_LOCK_ACCT_FOR_UPDATE, request);
        }
        Optional<CustomerEntity> lockedCustomer =
                customerRepository.lockByCustId(records.customer().getCustId());
        if (lockedCustomer.isEmpty()) {
            return failure(AccountUpdateStatus.LOCK_ERROR,
                    ScreenMessages.COULD_NOT_LOCK_CUST_FOR_UPDATE, request);
        }

        AccountEntity account = lockedAccount.get();
        CustomerEntity customer = lockedCustomer.get();

        // 9700-CHECK-CHANGE-IN-REC: the locked records must still match the values the client was
        // shown, otherwise another updater changed them in the meantime.
        AccountUpdateForm stored = accountScreenMapper.toForm(account, customer);
        if (accountChangeDetector.hasChanges(request.original(), stored)) {
            return new AccountUpdateResponse(AccountUpdateStatus.RECORD_CHANGED,
                    ScreenMessages.PROMPT_FOR_CHANGES, ScreenMessages.DATA_WAS_CHANGED_BEFORE_UPDATE,
                    stored, stored, null, Map.of());
        }

        applyToAccount(account, validated);
        applyToCustomer(customer, validated);
        try {
            accountRepository.saveAndFlush(account);
            customerRepository.saveAndFlush(customer);
        } catch (RuntimeException failed) {
            throw new UpdateFailedException(ScreenMessages.LOCKED_BUT_UPDATE_FAILED, failed);
        }

        AccountUpdateForm committed = accountScreenMapper.toForm(account, customer);
        return new AccountUpdateResponse(AccountUpdateStatus.CHANGES_COMMITTED,
                ScreenMessages.CONFIRM_UPDATE_SUCCESS, null, committed, committed,
                accountScreenMapper.toDetails(account, customer, records.xref().getXrefCardNum()),
                Map.of());
    }

    private AccountUpdateResponse failure(AccountUpdateStatus status, String message,
                                          AccountUpdateRequest request) {
        return new AccountUpdateResponse(status, ScreenMessages.INFORM_FAILURE, message,
                request.original(), request.updated(), null, Map.of());
    }

    /** 9600-WRITE-PROCESSING, ACCT-UPDATE-RECORD block. */
    private static void applyToAccount(AccountEntity account, ValidatedAccountUpdate values) {
        account.setActiveStatus(values.activeStatus());
        account.setCurrBal(values.currentBalance());
        account.setCreditLimit(values.creditLimit());
        account.setCashCreditLimit(values.cashCreditLimit());
        account.setCurrCycCredit(values.currentCycleCredit());
        account.setCurrCycDebit(values.currentCycleDebit());
        account.setOpenDate(values.openDate());
        account.setExpirationDate(values.expirationDate());
        account.setReissueDate(values.reissueDate());
        account.setGroupId(values.groupId());
    }

    /** 9600-WRITE-PROCESSING, CUST-UPDATE-RECORD block. */
    private static void applyToCustomer(CustomerEntity customer, ValidatedAccountUpdate values) {
        customer.setFirstName(values.firstName());
        customer.setMiddleName(values.middleName());
        customer.setLastName(values.lastName());
        customer.setAddrLine1(values.addressLine1());
        customer.setAddrLine2(values.addressLine2());
        customer.setAddrLine3(values.city());
        customer.setAddrStateCd(values.state());
        customer.setAddrCountryCd(values.country());
        customer.setAddrZip(values.zip());
        customer.setPhoneNum1(values.phoneNum1());
        customer.setPhoneNum2(values.phoneNum2());
        customer.setSsn(values.ssn());
        customer.setGovtIssuedId(values.governmentIssuedId());
        customer.setDobYyyyMmDd(values.dateOfBirth());
        customer.setEftAccountId(values.eftAccountId());
        customer.setPriCardHolderInd(values.primaryCardHolder());
        customer.setFicoCreditScore(values.ficoScore());
    }
}
