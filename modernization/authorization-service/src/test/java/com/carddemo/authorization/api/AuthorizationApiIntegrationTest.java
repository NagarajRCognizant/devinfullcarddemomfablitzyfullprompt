package com.carddemo.authorization.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.AuthorizationPostgresIntegrationTest;
import com.carddemo.authorization.client.CardholderLookupClient;
import com.carddemo.authorization.client.CardholderLookupException;
import com.carddemo.authorization.client.CardholderView;
import com.carddemo.authorization.domain.AuthorizationKey;
import com.carddemo.authorization.domain.AuthorizationMessages;
import com.carddemo.authorization.domain.FraudStatus;
import com.carddemo.persistence.entity.AuthorizationDetailEntity;
import com.carddemo.persistence.entity.AuthorizationDetailId;
import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import com.carddemo.persistence.repository.FraudReportRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Transactions CPVS and CPVD over HTTP: the account edits, the five rows per page, the paging
 * messages, the edited detail fields and the fraud toggle, all against PostgreSQL.
 */
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_USER")
class AuthorizationApiIntegrationTest extends AuthorizationPostgresIntegrationTest {

    private static final long ACCOUNT = 77L;
    private static final String CARD = "4111111111111111";

    @MockBean
    private CardholderLookupClient cardholderLookupClient;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AuthorizationSummaryRepository summaryRepository;
    @Autowired
    private AuthorizationDetailRepository detailRepository;
    @Autowired
    private FraudReportRepository fraudReportRepository;

    /** Twelve authorizations, one per minute, so the complemented key orders them newest first. */
    @BeforeEach
    void seedAuthorizations() {
        given(cardholderLookupClient.byAccountId(anyLong())).willReturn(new CardholderView(
                true, true, true, CARD, ACCOUNT, 88L, "Y", new BigDecimal("5000.00"),
                new BigDecimal("500.00"), new BigDecimal("100.00"), "JOHN", "QUINCY", "PUBLIC",
                "1 MAIN ST", "APT 2", "DALLAS", "TX", "750011234", "2145551212"));

        AuthorizationSummaryEntity summary = new AuthorizationSummaryEntity();
        summary.setAcctId(ACCOUNT);
        summary.setCustId(88L);
        summary.setCreditLimit(new BigDecimal("5000.00"));
        summary.setCashLimit(new BigDecimal("500.00"));
        summary.setCreditBalance(new BigDecimal("250.00"));
        summary.setCashBalance(BigDecimal.ZERO);
        summary.setApprovedAuthCnt((short) 10);
        summary.setDeclinedAuthCnt((short) 2);
        summary.setApprovedAuthAmt(new BigDecimal("250.00"));
        summary.setDeclinedAuthAmt(new BigDecimal("40.00"));
        summaryRepository.saveAndFlush(summary);

        for (int i = 0; i < 12; i++) {
            LocalDateTime stamp = LocalDateTime.of(2024, 3, 1, 10, i, 30);
            boolean approved = i >= 2;
            detailRepository.save(detail(stamp, approved, i));
        }
        detailRepository.flush();
    }

    private static AuthorizationDetailEntity detail(LocalDateTime stamp, boolean approved, int index) {
        AuthorizationKey key = AuthorizationKey.of(stamp);
        AuthorizationDetailEntity detail = new AuthorizationDetailEntity();
        detail.setId(new AuthorizationDetailId(ACCOUNT, key.value()));
        detail.setAuthDate9c(key.date9c());
        detail.setAuthTime9c(key.time9c());
        detail.setAuthOrigDate("240301");
        detail.setAuthOrigTime("10%02d30".formatted(stamp.getMinute()));
        detail.setCardNum(CARD);
        detail.setAuthType("0100");
        detail.setCardExpiryDate("1225");
        detail.setMessageSource("POS");
        detail.setProcessingCode("000000");
        detail.setPosEntryMode("05");
        detail.setMerchantCategoryCode("5411");
        detail.setAuthRespCode(approved ? "00" : "05");
        detail.setAuthRespReason(approved ? "0000" : "4100");
        detail.setTransactionAmt(new BigDecimal("20.00"));
        detail.setApprovedAmt(approved ? new BigDecimal("20.00") : BigDecimal.ZERO);
        detail.setMerchantId("MERCH000000001");
        detail.setMerchantName("ACME STORE");
        detail.setMerchantCity("DALLAS");
        detail.setMerchantState("TX");
        detail.setMerchantZip("75001");
        detail.setTransactionId("TRAN%011d".formatted(index));
        detail.setMatchStatus(approved ? "P" : "D");
        detail.setAuthFraud(" ");
        detail.setFraudRptDate(" ");
        return detail;
    }

    /** Every CardDemo transaction was reachable only after COSGN00C signed the user on. */
    @Test
    @WithAnonymousUser
    void anUnauthenticatedCallIsRejectedBeforeTheScreenIsReached() throws Exception {
        mockMvc.perform(get("/api/authorizations").param("accountId", "77"))
                .andExpect(status().isUnauthorized());
    }

    /** {@code IF PAUS0-ACCTID = SPACES OR LOW-VALUES} of PROCESS-ENTER-KEY. */
    @Test
    void anEmptyAccountIdIsRejectedWithTheSourcePrompt() throws Exception {
        mockMvc.perform(get("/api/authorizations"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AuthorizationMessages.PLEASE_ENTER_ACCT_ID));
    }

    /** {@code IF PAUS0-ACCTID IS NOT NUMERIC}. */
    @Test
    void aNonNumericAccountIdIsRejectedWithTheSourceMessage() throws Exception {
        mockMvc.perform(get("/api/authorizations").param("accountId", "7A7"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(AuthorizationMessages.ACCT_ID_MUST_BE_NUMERIC));
    }

    @Test
    void theFirstPageShowsTheHeaderTheTotalsAndFiveNewestAuthorizations() throws Exception {
        mockMvc.perform(get("/api/authorizations").param("accountId", "77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("JOHN Q PUBLIC"))
                .andExpect(jsonPath("$.addressLine1").value("1 MAIN ST,APT 2"))
                .andExpect(jsonPath("$.addressLine2").value("DALLAS,TX,75001"))
                .andExpect(jsonPath("$.creditLimit.display").value("+      5,000.00"))
                .andExpect(jsonPath("$.approvedCount").value(10))
                .andExpect(jsonPath("$.declinedCount").value(2))
                .andExpect(jsonPath("$.summaryFound").value(true))
                .andExpect(jsonPath("$.authorizations.length()").value(5))
                .andExpect(jsonPath("$.authorizations[0].transactionId").value("TRAN00000000011"))
                .andExpect(jsonPath("$.authorizations[0].authorizationTime").value("10:11:30"))
                .andExpect(jsonPath("$.authorizations[0].authorizationDate").value("03/01/24"))
                .andExpect(jsonPath("$.authorizations[0].approvalStatus").value("A"))
                .andExpect(jsonPath("$.authorizations[4].transactionId").value("TRAN00000000007"))
                .andExpect(jsonPath("$.morePages").value(true))
                .andExpect(jsonPath("$.message").doesNotExist());
    }

    /** PROCESS-PF8-KEY paged forward from the last key on the page. */
    @Test
    void pagingForwardContinuesAfterTheLastKeyOfThePreviousPage() throws Exception {
        String lastKey = lastAuthKeyOfFirstPage();

        mockMvc.perform(get("/api/authorizations")
                        .param("accountId", "77")
                        .param("afterAuthKey", lastKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizations.length()").value(5))
                .andExpect(jsonPath("$.authorizations[0].transactionId").value("TRAN00000000006"))
                .andExpect(jsonPath("$.morePages").value(true));
    }

    /**
     * {@code IF NEXT-PAGE-NO MOVE 'You are already at the bottom of the page...'}: the twelfth
     * authorization is the last, so the page after it is empty and carries that message.
     */
    @Test
    void pagingPastTheLastAuthorizationReportsTheBottomOfThePage() throws Exception {
        String key = authKeyOf(LocalDateTime.of(2024, 3, 1, 10, 0, 30));

        mockMvc.perform(get("/api/authorizations")
                        .param("accountId", "77")
                        .param("afterAuthKey", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizations.length()").value(0))
                .andExpect(jsonPath("$.morePages").value(false))
                .andExpect(jsonPath("$.message").value(AuthorizationMessages.BOTTOM_OF_PAGE));
    }

    /** An account with no PAUTSUM0 segment shows zeroes, as the initialised map fields did. */
    @Test
    void anAccountWithoutASummarySegmentShowsZeroTotals() throws Exception {
        detailRepository.deleteAll();
        summaryRepository.deleteAll();

        mockMvc.perform(get("/api/authorizations").param("accountId", "77"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaryFound").value(false))
                .andExpect(jsonPath("$.approvedCount").value(0))
                .andExpect(jsonPath("$.creditBalance.amount").value(0))
                .andExpect(jsonPath("$.authorizations.length()").value(0));
    }

    /** {@code IF PAUS0-SELECT-FLAG NOT = 'S' AND NOT = 's'} of PROCESS-ENTER-KEY. */
    @Test
    void aSelectionOtherThanSIsRejected() throws Exception {
        mockMvc.perform(get("/api/authorizations/{acct}/{key}", ACCOUNT, lastAuthKeyOfFirstPage())
                        .param("selection", "X"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(AuthorizationMessages.INVALID_SELECTION));
    }

    @Test
    void theDetailScreenShowsTheEditedAuthorizationFields() throws Exception {
        String key = authKeyOf(LocalDateTime.of(2024, 3, 1, 10, 1, 30));

        mockMvc.perform(get("/api/authorizations/{acct}/{key}", ACCOUNT, key).param("selection", "s"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumber").value(CARD))
                .andExpect(jsonPath("$.authorizationDate").value("03/01/24"))
                .andExpect(jsonPath("$.authorizationTime").value("10:01:30"))
                .andExpect(jsonPath("$.cardExpiryDate").value("12/25"))
                .andExpect(jsonPath("$.responseCode").value("05"))
                .andExpect(jsonPath("$.responseReason").value("4100-INSUFFICNT FUND"))
                .andExpect(jsonPath("$.approvalStatus").value("D"))
                .andExpect(jsonPath("$.fraudStatus").value("-"))
                .andExpect(jsonPath("$.transactionAmount.display").value("+         20.00"))
                .andExpect(jsonPath("$.nextAuthKey").exists());
    }

    /** {@code IF PA-AUTH-EOR MOVE 'Already at the last Authorization...'} of PROCESS-PF8-KEY. */
    @Test
    void theOldestAuthorizationReportsThatItIsTheLastOne() throws Exception {
        String oldest = authKeyOf(LocalDateTime.of(2024, 3, 1, 10, 0, 30));

        mockMvc.perform(get("/api/authorizations/{acct}/{key}", ACCOUNT, oldest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAuthKey").doesNotExist())
                .andExpect(jsonPath("$.message")
                        .value(AuthorizationMessages.LAST_AUTHORIZATION));
    }

    @Test
    void anAuthorizationThatIsNotOnTheAccountIsReportedAsNotFound() throws Exception {
        mockMvc.perform(get("/api/authorizations/{acct}/{key}", ACCOUNT, "99999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value(AuthorizationMessages.authorizationNotFound("99999999999999")));
    }

    /**
     * PF5 of COPAUS1C, which started COPAUS2C: the flag becomes F, the report date is stamped and
     * the DB2 fraud row is written. A second press reverses it to R on the same row.
     */
    @Test
    void markingAndUnmarkingFraudTogglesTheFlagAndKeepsOneFraudRow() throws Exception {
        String key = authKeyOf(LocalDateTime.of(2024, 3, 1, 10, 5, 30));

        mockMvc.perform(post("/api/authorizations/{acct}/{key}/fraud", ACCOUNT, key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(AuthorizationMessages.FRAUD_MARKED))
                .andExpect(jsonPath("$.fraudStatus").value(org.hamcrest.Matchers.startsWith("F-")));

        AuthorizationDetailEntity marked = detailRepository
                .findById(new AuthorizationDetailId(ACCOUNT, key)).orElseThrow();
        assertThat(marked.getAuthFraud()).isEqualTo(FraudStatus.CONFIRMED);
        assertThat(fraudReportRepository.count()).isEqualTo(1);

        mockMvc.perform(post("/api/authorizations/{acct}/{key}/fraud", ACCOUNT, key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(AuthorizationMessages.FRAUD_REMOVED))
                .andExpect(jsonPath("$.fraudStatus").value(org.hamcrest.Matchers.startsWith("R-")));

        AuthorizationDetailEntity removed = detailRepository
                .findById(new AuthorizationDetailId(ACCOUNT, key)).orElseThrow();
        assertThat(removed.getAuthFraud()).isEqualTo(FraudStatus.REMOVED);
        // SQLCODE -803 on the insert sent COPAUS2C to the update path: still one row per
        // (card number, authorization timestamp).
        assertThat(fraudReportRepository.count()).isEqualTo(1);
    }

    /**
     * The account reads are remote now. A failure is reported as a bad gateway rather than being
     * treated as "record not found", which would have shown the wrong screen message.
     */
    @Test
    void aFailureOfTheAccountServiceIsReportedAsAnUpstreamFailure() throws Exception {
        given(cardholderLookupClient.byAccountId(anyLong()))
                .willThrow(new CardholderLookupException("account service unavailable", null));

        mockMvc.perform(get("/api/authorizations").param("accountId", "77"))
                .andExpect(status().isBadGateway());
    }

    /** GETCARDXREF-BYACCT NOTFND: the source's own account-not-found message. */
    @Test
    void anAccountThatIsNotInTheCrossReferenceIsReportedWithTheSourceMessage() throws Exception {
        given(cardholderLookupClient.byAccountId(anyLong()))
                .willReturn(CardholderView.notFound("77"));

        mockMvc.perform(get("/api/authorizations").param("accountId", "77"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value(AuthorizationMessages.accountNotFoundInXref("77")));
    }

    private static String authKeyOf(LocalDateTime stamp) {
        return AuthorizationKey.of(stamp).value();
    }

    private static String lastAuthKeyOfFirstPage() {
        return authKeyOf(LocalDateTime.of(2024, 3, 1, 10, 7, 30));
    }
}
