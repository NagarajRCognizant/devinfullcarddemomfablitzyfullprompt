package com.carddemo.transaction.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.carddemo.persistence.entity.ReportRequestEntity;
import com.carddemo.persistence.repository.ReportRequestRepository;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.TransactionPostgresIntegrationTest;
import com.carddemo.transaction.api.dto.BillPaymentRequest;
import com.carddemo.transaction.api.dto.ReportRequestForm;
import com.carddemo.transaction.api.dto.TransactionAddRequest;
import com.carddemo.transaction.client.AccountServiceClient;
import com.carddemo.transaction.client.BalanceAdjustmentResult;
import com.carddemo.transaction.client.CardholderView;
import com.carddemo.transaction.domain.TransactionIdentifiers;
import com.carddemo.transaction.domain.TransactionMessages;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Transactions CT00, CT01, CT02, CB00 and CR00 over HTTP against the seeded PostgreSQL database:
 * the ten rows per page, the paging messages, the edited detail fields, the generated key and the
 * bill payment, with the account service stubbed at the client boundary.
 */
@AutoConfigureMockMvc
@WithMockUser(username = "USER0001", authorities = "ROLE_USER")
class TransactionApiIntegrationTest extends TransactionPostgresIntegrationTest {

    private static final String CARD = "4859452612877065";
    private static final long ACCOUNT = 1L;
    private static final String ACCOUNT_KEYED = "00000000001";

    /** Keys of the supplied sample data, in the character order the browse follows. */
    private static final String FIRST_KEY = "0000000000683580";
    private static final String TENTH_KEY = "0000000021711604";
    private static final String ELEVENTH_KEY = "0000000025430891";
    private static final String MIDDLE_KEY = "0000000010142252";

    @MockBean
    private AccountServiceClient accounts;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TransactionRepository transactions;
    @Autowired
    private ReportRequestRepository reportRequests;

    @BeforeEach
    void stubTheAccountService() {
        when(accounts.byAccountId(ACCOUNT))
                .thenReturn(CardholderView.found(CARD, ACCOUNT, new BigDecimal("1200.50")));
        when(accounts.byCardNumber(CARD))
                .thenReturn(CardholderView.found(CARD, ACCOUNT, new BigDecimal("1200.50")));
        when(accounts.adjustBalance(anyLong(), any())).thenReturn(
                new BalanceAdjustmentResult(ACCOUNT, true, BigDecimal.ZERO, BigDecimal.ZERO,
                        new BigDecimal("1200.50")));
    }

    /** Every screen was reachable only after COSGN00C accepted the user id and password. */
    @Test
    @WithAnonymousUser
    void anUnauthenticatedCallIsRejectedBeforeTheScreenIsReached() throws Exception {
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    void theListShowsTenRowsInKeyOrderWithTheEditedDateAndAmount() throws Exception {
        mockMvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(10))
                .andExpect(jsonPath("$.pageNumber").value(1))
                .andExpect(jsonPath("$.rows[0].tranId").value(FIRST_KEY))
                .andExpect(jsonPath("$.rows[9].tranId").value(TENTH_KEY))
                .andExpect(jsonPath("$.rows[0].amount.display").value("+00000504.77"))
                .andExpect(jsonPath("$.rows[0].date").value("06/10/22"));
    }

    /** PF8 continued the browse from the last key shown. */
    @Test
    void theForwardKeyShowsTheFollowingPage() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("direction", "next")
                        .param("lastTranId", TENTH_KEY)
                        .param("pageNumber", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(2))
                .andExpect(jsonPath("$.rows[0].tranId").value(ELEVENTH_KEY));
    }

    /** PF7 on the first page was refused with the source's own message. */
    @Test
    void theBackwardKeyOnTheFirstPageSaysSoAndStays() throws Exception {
        mockMvc.perform(get("/api/transactions")
                        .param("direction", "previous")
                        .param("firstTranId", FIRST_KEY)
                        .param("pageNumber", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNumber").value(1))
                .andExpect(jsonPath("$.rows[0].tranId").value(FIRST_KEY))
                .andExpect(jsonPath("$.message").value(TransactionMessages.ALREADY_AT_TOP));
    }

    /** PF8 past the last record was refused the same way. */
    @Test
    void theForwardKeyOnTheLastPageSaysSoAndStays() throws Exception {
        String highest = transactions.findHighestTransactionId().orElseThrow();

        mockMvc.perform(get("/api/transactions")
                        .param("direction", "next")
                        .param("lastTranId", highest)
                        .param("pageNumber", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(TransactionMessages.ALREADY_AT_BOTTOM));
    }

    @Test
    void aKeyedTransactionIdentifierPositionsTheBrowse() throws Exception {
        mockMvc.perform(get("/api/transactions").param("tranId", MIDDLE_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].tranId").value(MIDDLE_KEY));
    }

    @Test
    void theDetailScreenShowsEveryEditedFieldOfTheRecord() throws Exception {
        mockMvc.perform(get("/api/transactions/{id}", FIRST_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tranId").value(FIRST_KEY))
                .andExpect(jsonPath("$.cardNumber").value(CARD))
                .andExpect(jsonPath("$.amount.display").value("+00000504.77"))
                .andExpect(jsonPath("$.merchantId").isNotEmpty());
    }

    @Test
    void anUnknownTransactionIsReportedWithTheSourceMessage() throws Exception {
        mockMvc.perform(get("/api/transactions/{id}", "0000000000099999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(TransactionMessages.TRANSACTION_NOT_FOUND));
    }

    @Test
    void anEmptyIdentifierIsRejectedByTheEdit() throws Exception {
        mockMvc.perform(get("/api/transactions/{id}", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(TransactionMessages.TRAN_ID_EMPTY));
    }

    @Test
    void aConfirmedAddWritesTheRecordWithTheNextKey() throws Exception {
        TransactionAddRequest request = new TransactionAddRequest(ACCOUNT_KEYED, null, "01",
                "0001", "POS TERM", "Integration test purchase", "+00000010.00", "2022-06-10",
                "2022-06-10", "800000000", "Abshire-Lowe", "North Enoshaven", "72112", "Y");

        String expectedKey =
                TransactionIdentifiers.next(transactions.findHighestTransactionId().orElseThrow());

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tranId").value(expectedKey))
                .andExpect(jsonPath("$.message")
                        .value(TransactionMessages.transactionAdded(expectedKey)));

        assertThat(transactions.findById(expectedKey)).isPresent();
    }

    @Test
    void theAddScreenRefusesAMalformedAmount() throws Exception {
        TransactionAddRequest request = new TransactionAddRequest(ACCOUNT_KEYED, null, "01",
                "0001", "POS TERM", "Integration test purchase", "10.00", "2022-06-10",
                "2022-06-10", "800000000", "Abshire-Lowe", "North Enoshaven", "72112", "Y");

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(TransactionMessages.AMOUNT_FORMAT));
    }

    @Test
    void theLastTransactionOfTheAccountFillsTheAddScreen() throws Exception {
        mockMvc.perform(get("/api/transactions/last").param("accountId", ACCOUNT_KEYED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardNumber").value(CARD))
                .andExpect(jsonPath("$.amount").isNotEmpty());
    }

    @Test
    void theBillPaymentScreenShowsTheBalanceAndAsksForConfirmation() throws Exception {
        mockMvc.perform(get("/api/bill-payments/{id}", ACCOUNT_KEYED))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentBalance.display").value("+00001200.50"))
                .andExpect(jsonPath("$.paid").value(false))
                .andExpect(jsonPath("$.message").value(TransactionMessages.CONFIRM_PAYMENT));
    }

    @Test
    void aConfirmedPaymentPostsOneTransactionAndIsNotPostedAgainOnReplay() throws Exception {
        BillPaymentRequest request =
                new BillPaymentRequest(ACCOUNT_KEYED, "Y", "bill-pay-integration-1");
        long before = transactions.count();
        String expectedKey =
                TransactionIdentifiers.next(transactions.findHighestTransactionId().orElseThrow());

        mockMvc.perform(post("/api/bill-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paid").value(true))
                .andExpect(jsonPath("$.tranId").value(expectedKey));

        mockMvc.perform(post("/api/bill-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tranId").value(expectedKey));

        assertThat(transactions.count()).isEqualTo(before + 1);
    }

    @Test
    void aConfirmedReportRequestIsRecordedForTheBatchJob() throws Exception {
        ReportRequestForm form = new ReportRequestForm("Custom", "06", "10", "2022", "06", "30",
                "2022", "Y");

        mockMvc.perform(post("/api/transaction-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(form)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(true))
                .andExpect(jsonPath("$.startDate").value("2022-06-10"))
                .andExpect(jsonPath("$.endDate").value("2022-06-30"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    /**
     * CORPT00C recorded the eight character SEC-USR-ID of the operator, which the realm carries as
     * the username; its own identifier of the user is a UUID and does not fit that column.
     */
    @Test
    void recordsTheEightCharacterUserIdOfTheRealmTokenAndNotItsSubject() throws Exception {
        ReportRequestForm form = new ReportRequestForm("Monthly", "", "", "", "", "", "", "Y");

        mockMvc.perform(post("/api/transaction-reports")
                        .with(jwt().jwt(token -> token
                                .subject("1d1d2b0e-9f4a-4a4f-9f0a-6f2f2e0d1c3b")
                                .claim("preferred_username", "user0001")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(form)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submitted").value(true));

        assertThat(reportRequests.findAll())
                .extracting(ReportRequestEntity::getRequestedBy)
                .contains("USER0001");
    }
}
