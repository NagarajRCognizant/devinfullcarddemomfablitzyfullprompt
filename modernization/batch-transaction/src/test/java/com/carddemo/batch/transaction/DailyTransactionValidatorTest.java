package com.carddemo.batch.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.CardholderView;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** The edits of paragraph 1500-VALIDATE-TRAN and their four reject reasons. */
class DailyTransactionValidatorTest {

    private static final String CARD = "4111111111111111";

    private final AccountServiceClient accounts = mock(AccountServiceClient.class);
    private final DailyTransactionValidator validator = new DailyTransactionValidator(accounts);

    @Test
    void acceptsATransactionThatIsWithinTheLimitAndBeforeExpiry() {
        given(account("2025-12-31", "5000.00", "1000.00", "200.00"));

        PostingCandidate candidate = validator.process(transaction("100.00", "2022-06-10"));

        assertThat(candidate.postable()).isTrue();
        assertThat(candidate.failReason()).isZero();
        assertThat(candidate.accountId()).isEqualTo(11L);
    }

    @Test
    void rejectsACardTheCrossReferenceDoesNotHold() {
        when(accounts.byCardNumber(anyString())).thenReturn(CardholderView.notFound(CARD));

        PostingCandidate candidate = validator.process(transaction("100.00", "2022-06-10"));

        assertThat(candidate.postable()).isFalse();
        assertThat(candidate.failReason()).isEqualTo(PostingCandidate.INVALID_CARD_NUMBER);
        assertThat(candidate.failReasonDescription()).isEqualTo("INVALID CARD NUMBER FOUND");
    }

    @Test
    void rejectsACardWhoseAccountIsMissing() {
        given(new CardholderView(true, false, false, CARD, 11L, null, null, null, null, null, null));

        PostingCandidate candidate = validator.process(transaction("100.00", "2022-06-10"));

        assertThat(candidate.failReason()).isEqualTo(PostingCandidate.ACCOUNT_NOT_FOUND);
        assertThat(candidate.failReasonDescription()).isEqualTo("ACCOUNT RECORD NOT FOUND");
    }

    /** Cycle credit less cycle debit plus the new amount may not exceed the credit limit. */
    @Test
    void rejectsATransactionThatTakesTheCycleOverTheCreditLimit() {
        given(account("2025-12-31", "1000.00", "900.00", "50.00"));

        PostingCandidate candidate = validator.process(transaction("200.00", "2022-06-10"));

        assertThat(candidate.failReason()).isEqualTo(PostingCandidate.OVERLIMIT);
        assertThat(candidate.failReasonDescription()).isEqualTo("OVERLIMIT TRANSACTION");
    }

    @Test
    void rejectsATransactionOriginatedAfterTheAccountExpired() {
        given(account("2022-01-31", "5000.00", "0.00", "0.00"));

        PostingCandidate candidate = validator.process(transaction("100.00", "2022-06-10"));

        assertThat(candidate.failReason()).isEqualTo(PostingCandidate.AFTER_EXPIRATION);
        assertThat(candidate.failReasonDescription())
                .isEqualTo("TRANSACTION RECEIVED AFTER ACCT EXPIRATION");
    }

    /** The source overwrites the reason, so expiry is reported when both edits fail. */
    @Test
    void reportsExpiryWhenTheTransactionIsBothOverLimitAndLate() {
        given(account("2022-01-31", "10.00", "0.00", "0.00"));

        PostingCandidate candidate = validator.process(transaction("100.00", "2022-06-10"));

        assertThat(candidate.failReason()).isEqualTo(PostingCandidate.AFTER_EXPIRATION);
    }

    private void given(CardholderView view) {
        when(accounts.byCardNumber(anyString())).thenReturn(view);
    }

    private CardholderView account(String expiry, String creditLimit, String cycleCredit,
            String cycleDebit) {
        return new CardholderView(true, true, true, CARD, 11L, new BigDecimal("100.00"),
                new BigDecimal(creditLimit), new BigDecimal(cycleCredit), new BigDecimal(cycleDebit),
                expiry, "ZEROAPR");
    }

    private DailyTransaction transaction(String amount, String originalDate) {
        return DailyTransaction.parse(DailyTransactionFixtures.record("0000000000000001", CARD,
                amount, originalDate + " 19:27:53.000000"));
    }
}
