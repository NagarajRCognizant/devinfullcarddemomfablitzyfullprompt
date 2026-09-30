package com.carddemo.service;

import com.carddemo.api.dto.StatementPartyResponse;
import com.carddemo.api.dto.StatementPartyResponse.StatementParty;
import com.carddemo.persistence.entity.AccountEntity;
import com.carddemo.persistence.entity.CardXrefEntity;
import com.carddemo.persistence.entity.CustomerEntity;
import com.carddemo.persistence.repository.AccountRepository;
import com.carddemo.persistence.repository.CardXrefRepository;
import com.carddemo.persistence.repository.CustomerRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The CARDXREF browse of CBSTM03A 1000-XREFFILE-GET-NEXT, together with the CUSTDAT and ACCTDAT
 * reads that follow each record, published for the statement job.
 *
 * <p>The source reads the cross reference to end of file one record at a time; a page of rows in
 * the same key order is the same sequence delivered in fewer calls, and the browse stays
 * positional because the sort is on the key of the cluster.
 */
@Service
public class StatementPartyService {

    private final CardXrefRepository cardXrefRepository;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    public StatementPartyService(CardXrefRepository cardXrefRepository,
                                 AccountRepository accountRepository,
                                 CustomerRepository customerRepository) {
        this.cardXrefRepository = cardXrefRepository;
        this.accountRepository = accountRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public StatementPartyResponse page(int pageNumber, int pageSize) {
        Page<CardXrefEntity> page = cardXrefRepository.findAll(
                PageRequest.of(pageNumber, pageSize, Sort.by("xrefCardNum").ascending()));
        List<StatementParty> rows = page.getContent().stream().map(this::readDownstream).toList();
        return new StatementPartyResponse(rows, page.isLast());
    }

    private StatementParty readDownstream(CardXrefEntity xref) {
        Optional<AccountEntity> account = accountRepository.findById(xref.getXrefAcctId());
        Optional<CustomerEntity> customer = customerRepository.findById(xref.getXrefCustId());
        return new StatementParty(
                xref.getXrefCardNum(),
                xref.getXrefAcctId(),
                xref.getXrefCustId(),
                account.isPresent(),
                customer.isPresent(),
                account.map(AccountEntity::getCurrBal).orElse(null),
                customer.map(CustomerEntity::getFicoCreditScore).orElse(null),
                customer.map(CustomerEntity::getFirstName).orElse(null),
                customer.map(CustomerEntity::getMiddleName).orElse(null),
                customer.map(CustomerEntity::getLastName).orElse(null),
                customer.map(CustomerEntity::getAddrLine1).orElse(null),
                customer.map(CustomerEntity::getAddrLine2).orElse(null),
                customer.map(CustomerEntity::getAddrLine3).orElse(null),
                customer.map(CustomerEntity::getAddrStateCd).orElse(null),
                customer.map(CustomerEntity::getAddrCountryCd).orElse(null),
                customer.map(CustomerEntity::getAddrZip).orElse(null));
    }
}
