package com.carddemo.service;

import com.carddemo.api.dto.CardholderLookupResponse;
import com.carddemo.persistence.entity.AccountEntity;
import com.carddemo.persistence.entity.CardXrefEntity;
import com.carddemo.persistence.entity.CustomerEntity;
import com.carddemo.persistence.repository.AccountRepository;
import com.carddemo.persistence.repository.CardXrefRepository;
import com.carddemo.persistence.repository.CustomerRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The CXACAIX / CARDXREF, ACCTDAT and CUSTDAT reads that the authorization programs performed
 * directly against the same VSAM files, published as a read only capability of this service.
 *
 * <p>The authorization bounded context owns only its own database, so it cannot repeat those
 * reads. Rather than share tables, this service exposes the records it owns and the authorization
 * service consumes them over HTTP; the read order and the per read outcome flags of
 * 9600-READ-CARDXREF, 9700-READ-ACCOUNT and 9800-READ-CUSTOMER are preserved in the response.
 */
@Service
public class CardholderLookupService {

    private final CardXrefRepository cardXrefRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public CardholderLookupService(CardXrefRepository cardXrefRepository, AccountRepository accountRepository,
                                   CustomerRepository customerRepository) {
        this.cardXrefRepository = cardXrefRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    /** COPAUA0C 9600-READ-CARDXREF: the cross reference is keyed by card number. */
    @Transactional(readOnly = true)
    public CardholderLookupResponse byCardNumber(String cardNumber) {
        return cardXrefRepository.findById(cardNumber)
                .map(this::readDownstream)
                .orElseGet(() -> CardholderLookupResponse.cardNotFound(cardNumber));
    }

    /** COPAUS0C GETCARDXREF-BYACCT: the same file read through the account id alternate index. */
    @Transactional(readOnly = true)
    public CardholderLookupResponse byAccountId(long accountId) {
        return cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc(accountId)
                .map(this::readDownstream)
                .orElseGet(() -> CardholderLookupResponse.cardNotFound(null));
    }

    private CardholderLookupResponse readDownstream(CardXrefEntity xref) {
        Optional<AccountEntity> account = accountRepository.findById(xref.getXrefAcctId());
        // The customer read only runs when the account read succeeded, as in the source.
        Optional<CustomerEntity> customer = account.isPresent()
                ? customerRepository.findById(xref.getXrefCustId())
                : Optional.empty();
        return new CardholderLookupResponse(
                true,
                account.isPresent(),
                customer.isPresent(),
                xref.getXrefCardNum(),
                xref.getXrefAcctId(),
                xref.getXrefCustId(),
                account.map(AccountEntity::getActiveStatus).orElse(null),
                account.map(AccountEntity::getCreditLimit).orElse(null),
                account.map(AccountEntity::getCashCreditLimit).orElse(null),
                account.map(AccountEntity::getCurrBal).orElse(null),
                account.map(AccountEntity::getCurrCycCredit).orElse(null),
                account.map(AccountEntity::getCurrCycDebit).orElse(null),
                account.map(AccountEntity::getExpirationDate).orElse(null),
                account.map(AccountEntity::getGroupId).orElse(null),
                customer.map(CustomerEntity::getFirstName).orElse(null),
                customer.map(CustomerEntity::getMiddleName).orElse(null),
                customer.map(CustomerEntity::getLastName).orElse(null),
                customer.map(CustomerEntity::getAddrLine1).orElse(null),
                customer.map(CustomerEntity::getAddrLine2).orElse(null),
                customer.map(CustomerEntity::getAddrLine3).orElse(null),
                customer.map(CustomerEntity::getAddrStateCd).orElse(null),
                customer.map(CustomerEntity::getAddrZip).orElse(null),
                customer.map(CustomerEntity::getPhoneNum1).orElse(null));
    }
}
