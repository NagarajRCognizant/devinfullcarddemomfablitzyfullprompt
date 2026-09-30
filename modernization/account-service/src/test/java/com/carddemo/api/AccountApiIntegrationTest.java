package com.carddemo.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carddemo.PostgresIntegrationTest;
import com.carddemo.api.dto.AccountUpdateForm;
import com.carddemo.api.dto.AccountUpdateRequest;
import com.carddemo.api.dto.AccountUpdateResponse;
import com.carddemo.api.dto.AccountUpdateStatus;
import com.carddemo.domain.ScreenMessages;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * The two converted transactions over HTTP, against the seeded database.
 *
 * <p>The update tests use accounts 9 and 12 because COACTUPC re-edits every field of the screen on
 * submit, and the supplied sample data holds FICO scores outside the source's 300-850 range for
 * several customers (customer 1 is 274, customer 2 is 268) and ZIP codes carrying a "+4" suffix that
 * the source's all-numeric ZIP edit rejects. Those records can be viewed but cannot be updated
 * without first correcting the offending field, which is source behaviour, not a defect of the
 * conversion; it is registered in the Unsupported Construct Register.
 */
@AutoConfigureMockMvc
@WithMockUser(authorities = "ROLE_USER")
class AccountApiIntegrationTest extends PostgresIntegrationTest {

    private static final String ACCOUNT = "00000000001";
    private static final String OTHER_ACCOUNT = "00000000002";
    private static final String UPDATABLE_ACCOUNT = "00000000009";
    private static final String OTHER_UPDATABLE_ACCOUNT = "00000000012";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    /** Both screens were reachable only after COSGN00C accepted the user id and password. */
    @Test
    @WithAnonymousUser
    void anUnauthenticatedCallIsRejectedBeforeTheScreenIsReached() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}", ACCOUNT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "USER0001", authorities = "ROLE_USER")
    void theMenuOffersBothAccountOptionsToARegularUser() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userType").value("U"))
                .andExpect(jsonPath("$.options[0].accessible").value(true))
                .andExpect(jsonPath("$.options[1].accessible").value(true));
    }

    /** COMEN01C reads CDEMO-USER-TYPE from the signed-on session, which is now the token. */
    @Test
    @WithMockUser(username = "ADMIN001", authorities = "ROLE_ADMIN")
    void theMenuEndpointDescribesTheUserTypeCarriedByTheToken() throws Exception {
        mockMvc.perform(get("/api/menu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("ADMIN001"))
                .andExpect(jsonPath("$.userType").value("A"))
                .andExpect(jsonPath("$.options.length()").value(11));
    }

    /**
     * The realm names a user by an identifier of its own, and the screens name one by the USRSEC key;
     * the menu has to show the key, as COMEN01C did with CDEMO-USER-ID.
     */
    @Test
    void theMenuNamesTheOperatorByTheUsrsecKeyAndNotByTheSubjectOfTheRealm() throws Exception {
        mockMvc.perform(get("/api/menu").with(jwt().jwt(token -> token
                        .subject("67c3df4b-b82d-4165-b812-93f53b0c5662")
                        .claim("preferred_username", "user0001"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("USER0001"));
    }

    @Test
    void theViewTransactionStartsWithThePrompt() throws Exception {
        mockMvc.perform(get("/api/accounts/view"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.infoMessage").value(ScreenMessages.VIEW_PROMPT_FOR_INPUT));
    }

    @Test
    void aKnownAccountIsReturnedWithItsCustomerAndCard() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}", ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.accountId").value(ACCOUNT))
                .andExpect(jsonPath("$.details.customerId").value("000000001"))
                .andExpect(jsonPath("$.details.cardNumber").value("9680294154603697"))
                .andExpect(jsonPath("$.details.currentBalance.amount").value(194.00));
    }

    @Test
    void aNonNumericAccountIdIsAValidationError() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}", "1234567890A"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ACCOUNT_ID_NOT_VALID"))
                .andExpect(jsonPath("$.message").value(ScreenMessages.VIEW_ACCT_FILTER_NOT_VALID))
                .andExpect(jsonPath("$.fieldFlags.accountId").value("NOT_OK"));
    }

    @Test
    void anUnknownAccountIsNotFound() throws Exception {
        mockMvc.perform(get("/api/accounts/{id}", "99999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message")
                        .value(ScreenMessages.accountNotInCrossReference("99999999999")))
                .andExpect(jsonPath("$.fieldFlags.accountId").value("NOT_OK"));
    }

    @Test
    void aBlankFilterIsTheBlankEditOfTheSearchOperation() throws Exception {
        mockMvc.perform(get("/api/accounts/search").param("accountId", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ACCOUNT_ID_BLANK"))
                .andExpect(jsonPath("$.message").value(ScreenMessages.PROMPT_FOR_ACCT))
                .andExpect(jsonPath("$.fieldFlags.accountId").value("BLANK"));
    }

    @Test
    void theSearchOperationReadsAKnownAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/search").param("accountId", ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.accountId").value(ACCOUNT));
    }

    @Test
    void aBlankFilterIsTheBlankEditOfTheUpdateFetch() throws Exception {
        mockMvc.perform(get("/api/accounts/update").param("accountId", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ACCOUNT_ID_BLANK"))
                .andExpect(jsonPath("$.fieldFlags.accountId").value("BLANK"));
    }

    @Test
    void theKeyedUpdateFetchReadsAKnownAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/update").param("accountId", UPDATABLE_ACCOUNT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DETAILS_FETCHED"))
                .andExpect(jsonPath("$.updated.accountId").value(UPDATABLE_ACCOUNT));
    }

    @Test
    void theUpdateTransactionFetchesValidatesAndCommits() throws Exception {
        AccountUpdateResponse fetched = fetch(UPDATABLE_ACCOUNT);
        assertThat(fetched.status()).isEqualTo(AccountUpdateStatus.DETAILS_FETCHED);

        AccountUpdateForm changed =
                withCreditLimit(screenCorrected(fetched.updated()), "3000.00");
        AccountUpdateResponse validated =
                submit(UPDATABLE_ACCOUNT, "validate", fetched.original(), changed);
        assertThat(validated.status()).isEqualTo(AccountUpdateStatus.CHANGES_VALIDATED);
        assertThat(validated.infoMessage()).isEqualTo(ScreenMessages.PROMPT_FOR_CONFIRMATION);

        AccountUpdateResponse committed =
                submit(UPDATABLE_ACCOUNT, "confirm", fetched.original(), changed);
        assertThat(committed.status()).isEqualTo(AccountUpdateStatus.CHANGES_COMMITTED);
        assertThat(committed.updated().creditLimit()).isEqualTo("3000.00");
        assertThat(fetch(UPDATABLE_ACCOUNT).updated().creditLimit()).isEqualTo("3000.00");
    }

    @Test
    void anUnchangedSubmissionIsReportedRatherThanRewritten() throws Exception {
        AccountUpdateResponse fetched = fetch(OTHER_ACCOUNT);

        AccountUpdateResponse response =
                submit(OTHER_ACCOUNT, "validate", fetched.original(), fetched.updated());

        assertThat(response.status()).isEqualTo(AccountUpdateStatus.NO_CHANGES);
        assertThat(response.errorMessage()).isEqualTo(ScreenMessages.NO_CHANGES_DETECTED);
    }

    @Test
    void aFailingFieldEditIsReturnedWithItsFlag() throws Exception {
        AccountUpdateResponse fetched = fetch(OTHER_ACCOUNT);

        AccountUpdateResponse response = submit(OTHER_ACCOUNT, "validate", fetched.original(),
                withCreditLimit(screenCorrected(fetched.updated()), "abc"));

        assertThat(response.status()).isEqualTo(AccountUpdateStatus.VALIDATION_ERROR);
        assertThat(response.fieldFlags()).containsKey("creditLimit");
    }

    /** The snapshot the caller sends back is compared with the stored record before the rewrite. */
    @Test
    void aRecordChangedByAnotherUpdaterIsNotOverwritten() throws Exception {
        AccountUpdateResponse fetched = fetch(OTHER_UPDATABLE_ACCOUNT);
        AccountUpdateForm staleSnapshot =
                withCreditLimit(screenCorrected(fetched.original()), "1111.00");

        AccountUpdateResponse response = submit(OTHER_UPDATABLE_ACCOUNT, "confirm", staleSnapshot,
                withCreditLimit(staleSnapshot, "2222.00"));

        assertThat(response.status()).isEqualTo(AccountUpdateStatus.RECORD_CHANGED);
        assertThat(response.errorMessage())
                .isEqualTo(ScreenMessages.DATA_WAS_CHANGED_BEFORE_UPDATE);
    }

    @Test
    void aMalformedBodyIsARequestError() throws Exception {
        mockMvc.perform(post("/api/accounts/{id}/update/validate", ACCOUNT)
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("MALFORMED_REQUEST"));
    }

    private AccountUpdateResponse fetch(String account) throws Exception {
        return call(get("/api/accounts/{id}/update", account));
    }

    private AccountUpdateResponse submit(String account, String action, AccountUpdateForm original,
                                         AccountUpdateForm updated) throws Exception {
        String body = objectMapper.writeValueAsString(new AccountUpdateRequest(original, updated));
        return call(post("/api/accounts/{id}/update/{action}", account, action)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private AccountUpdateResponse call(RequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readValue(result.getResponse().getContentAsString(),
                AccountUpdateResponse.class);
    }

    private static AccountUpdateForm withCreditLimit(AccountUpdateForm form, String creditLimit) {
        return new AccountUpdateForm(form.accountId(), form.activeStatus(), form.currentBalance(),
                creditLimit, form.cashCreditLimit(), form.openYear(), form.openMonth(),
                form.openDay(), form.expiryYear(), form.expiryMonth(), form.expiryDay(),
                form.reissueYear(), form.reissueMonth(), form.reissueDay(),
                form.currentCycleCredit(), form.currentCycleDebit(), form.groupId(),
                form.customerId(), form.ssnPart1(), form.ssnPart2(), form.ssnPart3(),
                form.dobYear(), form.dobMonth(), form.dobDay(), form.ficoScore(),
                form.firstName(), form.middleName(), form.lastName(), form.addressLine1(),
                form.addressLine2(), form.city(), form.state(), form.zip(), form.country(),
                form.phone1Area(), form.phone1Prefix(), form.phone1Line(), form.phone2Area(),
                form.phone2Prefix(), form.phone2Line(), form.governmentIssuedId(),
                form.eftAccountId(), form.primaryCardHolder());
    }

    /**
     * The corrections a user has to key before COACTUPC accepts the record. The supplied sample
     * data carries FICO scores outside 300-850, ZIP codes with a "+4" suffix and area codes that
     * are not in the CSLKPCDY general purpose table, and SSNs whose first group the source rejects,
     * so a re-edit of an untouched screen fails.
     */
    private static AccountUpdateForm screenCorrected(AccountUpdateForm form) {
        return new AccountUpdateForm(form.accountId(), form.activeStatus(), form.currentBalance(),
                form.creditLimit(), form.cashCreditLimit(), form.openYear(), form.openMonth(),
                form.openDay(), form.expiryYear(), form.expiryMonth(), form.expiryDay(),
                form.reissueYear(), form.reissueMonth(), form.reissueDay(),
                form.currentCycleCredit(), form.currentCycleDebit(), form.groupId(),
                form.customerId(), "123", "45", "6789",
                form.dobYear(), form.dobMonth(), form.dobDay(), "700",
                form.firstName(), form.middleName(), form.lastName(), form.addressLine1(),
                form.addressLine2(), form.city(), "NC", "27610", form.country(),
                "201", form.phone1Prefix(), form.phone1Line(), "201",
                form.phone2Prefix(), form.phone2Line(), form.governmentIssuedId(),
                form.eftAccountId(), form.primaryCardHolder());
    }
}
