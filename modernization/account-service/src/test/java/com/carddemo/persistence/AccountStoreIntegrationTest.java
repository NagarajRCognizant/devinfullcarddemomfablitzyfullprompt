package com.carddemo.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.PostgresIntegrationTest;
import com.carddemo.persistence.entity.AccountEntity;
import com.carddemo.persistence.entity.CardXrefEntity;
import com.carddemo.persistence.entity.CustomerEntity;
import com.carddemo.persistence.repository.AccountRepository;
import com.carddemo.persistence.repository.CardXrefRepository;
import com.carddemo.persistence.repository.CustomerRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/**
 * The migrated stores against a clean database: Flyway builds the schema and loads the sample data
 * that the IDCAMS REPRO steps of app/jcl/ACCTFILE.jcl loaded into the KSDS clusters.
 */
class AccountStoreIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private CardXrefRepository cardXrefRepository;

    @Test
    void everySampleRecordIsLoadedFromTheSuppliedAsciiFiles() {
        assertThat(accountRepository.count()).isEqualTo(50);
        assertThat(customerRepository.count()).isEqualTo(50);
        assertThat(cardXrefRepository.count()).isEqualTo(50);
    }

    /** Reconciliation against app/data/ASCII/acctdata.txt record one, zoned decimals included. */
    @Test
    void theFirstAccountReconcilesWithTheSampleRecord() {
        AccountEntity account = accountRepository.findById(1L).orElseThrow();

        assertThat(account.getActiveStatus()).isEqualTo("Y");
        assertThat(account.getCurrBal()).isEqualByComparingTo(new BigDecimal("194.00"));
        assertThat(account.getCreditLimit()).isEqualByComparingTo(new BigDecimal("2020.00"));
        assertThat(account.getCashCreditLimit()).isEqualByComparingTo(new BigDecimal("1020.00"));
        assertThat(account.getOpenDate()).isEqualTo("2014-11-20");
        assertThat(account.getExpirationDate()).isEqualTo("2025-05-20");
        assertThat(account.getReissueDate()).isEqualTo("2025-05-20");
    }

    /** A nine digit SSN keeps its leading zeroes, which an integer column would have dropped. */
    @Test
    void theCustomerRecordKeepsTheFixedWidthCharacterFields() {
        CustomerEntity customer = customerRepository.findById(47L).orElseThrow();

        assertThat(customer.getSsn()).hasSize(9).isEqualTo("029222192");
        assertThat(customer.getGovtIssuedId().strip()).isEqualTo("00000000000567601472");
        assertThat(customer.getFicoCreditScore()).isEqualTo(722);
    }

    /** 9200-READ-CARDXREF-ACCT reads through the account alternate index path. */
    @Test
    void theCrossReferenceIsReachableByAccount() {
        Optional<CardXrefEntity> xref =
                cardXrefRepository.findFirstByXrefAcctIdOrderByXrefCardNumAsc(40L);

        assertThat(xref).isPresent();
        assertThat(xref.orElseThrow().getXrefCardNum()).isEqualTo("9805583408996588");
        assertThat(xref.orElseThrow().getXrefCustId()).isEqualTo(40L);
    }

    /** 1000-ACCTFILE-GET-NEXT: the KSDS is read in ascending key order. */
    @Test
    void theForwardReadFollowsTheKeySequence() {
        List<AccountEntity> firstPage = accountRepository.readForward(PageRequest.of(0, 5));

        assertThat(firstPage).extracting(AccountEntity::getAcctId)
                .containsExactly(1L, 2L, 3L, 4L, 5L);
    }

    @Test
    @Transactional
    void bothRecordsCanBeLockedForUpdate() {
        assertThat(accountRepository.lockByAcctId(1L)).isPresent();
        assertThat(customerRepository.lockByCustId(1L)).isPresent();
        assertThat(accountRepository.lockByAcctId(99999999999L)).isEmpty();
    }
}
