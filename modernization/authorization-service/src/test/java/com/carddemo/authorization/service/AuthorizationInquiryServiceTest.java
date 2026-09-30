package com.carddemo.authorization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.carddemo.authorization.api.dto.AuthorizationDetailResponse;
import com.carddemo.authorization.api.dto.AuthorizationSummaryResponse;
import com.carddemo.authorization.client.CardholderLookupClient;
import com.carddemo.authorization.client.CardholderView;
import com.carddemo.authorization.domain.AuthorizationMessages;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.persistence.entity.AuthorizationDetailEntity;
import com.carddemo.persistence.entity.AuthorizationDetailId;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The edited output paths of POPULATE-AUTH-LIST and POPULATE-AUTH-DETAILS on the data the segments
 * actually held in the source: a segment written before a field existed carries low values, so the
 * map fields were built from short or blank values as well as complete ones. The reads are mocked
 * because the subject here is the formatting and the missing-record messages, not the persistence
 * mapping the integration tests already cover.
 */
@ExtendWith(MockitoExtension.class)
class AuthorizationInquiryServiceTest {

    private static final long ACCOUNT_ID = 77L;

    @Mock
    private CardholderLookupClient cardholderLookupClient;

    @Mock
    private AuthorizationSummaryRepository summaryRepository;

    @Mock
    private AuthorizationDetailRepository detailRepository;

    @InjectMocks
    private AuthorizationInquiryService service;

    @Test
    void summaryOfAnAccountWithoutASummarySegmentShowsZeroTotalsAndTheCustomerHeader() {
        when(cardholderLookupClient.byAccountId(ACCOUNT_ID)).thenReturn(cardholder());
        when(summaryRepository.findById(ACCOUNT_ID)).thenReturn(Optional.empty());
        when(detailRepository.findChildren(anyLong(), any())).thenReturn(List.of());

        AuthorizationSummaryResponse response = service.summary(ACCOUNT_ID, null);

        assertThat(response.summaryFound()).isFalse();
        assertThat(response.approvedCount()).isZero();
        assertThat(response.declinedCount()).isZero();
        assertThat(response.creditBalance().amount()).isEqualByComparingTo("0.00");
        assertThat(response.creditLimit().amount()).isEqualByComparingTo("0.00");
        // A blank middle name leaves no initial, and the zip is cut to the five bytes of the map.
        assertThat(response.customerName()).isEqualTo("ANN  LEE");
        assertThat(response.addressLine2()).isEqualTo("SEATTLE,WA,98101");
        assertThat(response.morePages()).isFalse();
        assertThat(response.message()).isNull();
    }

    @Test
    void detailBuiltFromASegmentWithShortDateTimeAndExpiryLeavesTheMapFieldsEmpty() {
        when(cardholderLookupClient.byAccountId(ACCOUNT_ID)).thenReturn(cardholder());
        AuthorizationDetailEntity detail = detail();
        detail.setAuthOrigDate("25");
        detail.setAuthOrigTime(null);
        detail.setCardExpiryDate("12");
        detail.setApprovedAmt(null);
        detail.setTransactionAmt(null);
        when(detailRepository.findById(new AuthorizationDetailId(ACCOUNT_ID, "KEY1")))
                .thenReturn(Optional.of(detail));
        when(detailRepository.findChildrenAfter(anyLong(), anyString(), any()))
                .thenReturn(List.of());

        AuthorizationDetailResponse response = service.detail(ACCOUNT_ID, "KEY1");

        assertThat(response.authorizationDate()).isEmpty();
        assertThat(response.authorizationTime()).isEmpty();
        assertThat(response.cardExpiryDate()).isEmpty();
        assertThat(response.approvedAmount().amount()).isEqualByComparingTo("0.00");
        assertThat(response.transactionAmount().amount()).isEqualByComparingTo("0.00");
        assertThat(response.fraudStatus()).isEqualTo("-");
        assertThat(response.nextAuthKey()).isNull();
        assertThat(response.message()).isEqualTo(AuthorizationMessages.LAST_AUTHORIZATION);
    }

    @Test
    void theFraudMessageIsReturnedWithTheKeyOfTheFollowingAuthorization() {
        AuthorizationDetailEntity detail = detail();
        AuthorizationDetailEntity next = detail();
        next.setId(new AuthorizationDetailId(ACCOUNT_ID, "KEY2"));
        when(detailRepository.findChildrenAfter(anyLong(), anyString(), any()))
                .thenReturn(List.of(next));

        AuthorizationDetailResponse response =
                service.detailWithMessage(detail, AuthorizationMessages.FRAUD_MARKED);

        assertThat(response.nextAuthKey()).isEqualTo("KEY2");
        assertThat(response.message()).isEqualTo(AuthorizationMessages.FRAUD_MARKED);
    }

    @Test
    void anAccountWhoseCustomerRecordIsMissingReturnsTheCustomerNotFoundMessage() {
        when(cardholderLookupClient.byAccountId(ACCOUNT_ID))
                .thenReturn(new CardholderView(true, true, false, "4111111111111111", ACCOUNT_ID,
                        555L, "Y", BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO, "ANN", " ",
                        "LEE", "1 MAIN ST", "APT 2", "SEATTLE", "WA", "981012345", "2065550100"));

        assertThatThrownBy(() -> service.summary(ACCOUNT_ID, null))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessage(AuthorizationMessages.customerNotFound("555"));
    }

    private static CardholderView cardholder() {
        return new CardholderView(true, true, true, "4111111111111111", ACCOUNT_ID, 555L, "Y",
                null, null, null, "ANN", " ", "LEE", "1 MAIN ST", "APT 2", "SEATTLE", "WA",
                "981012345", "2065550100");
    }

    private static AuthorizationDetailEntity detail() {
        AuthorizationDetailEntity detail = new AuthorizationDetailEntity();
        detail.setId(new AuthorizationDetailId(ACCOUNT_ID, "KEY1"));
        detail.setAuthRespCode("00");
        detail.setAuthRespReason("0000");
        detail.setMatchStatus("P");
        detail.setAuthType("01");
        return detail;
    }
}
