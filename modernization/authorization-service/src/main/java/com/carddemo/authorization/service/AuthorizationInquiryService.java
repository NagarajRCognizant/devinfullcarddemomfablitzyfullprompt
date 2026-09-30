package com.carddemo.authorization.service;

import com.carddemo.authorization.api.dto.MoneyValue;
import com.carddemo.authorization.api.dto.AuthorizationDetailResponse;
import com.carddemo.authorization.api.dto.AuthorizationListRow;
import com.carddemo.authorization.api.dto.AuthorizationSummaryResponse;
import com.carddemo.authorization.client.CardholderLookupClient;
import com.carddemo.authorization.client.CardholderView;
import com.carddemo.authorization.domain.AuthorizationMessages;
import com.carddemo.authorization.domain.DeclineReason;
import com.carddemo.authorization.domain.FraudStatus;
import com.carddemo.exception.RecordNotFoundException;
import com.carddemo.persistence.entity.AuthorizationDetailEntity;
import com.carddemo.persistence.entity.AuthorizationDetailId;
import com.carddemo.persistence.entity.AuthorizationSummaryEntity;
import com.carddemo.persistence.repository.AuthorizationDetailRepository;
import com.carddemo.persistence.repository.AuthorizationSummaryRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactions CPVS (COPAUS0C) and CPVD (COPAUS1C) as read side application services.
 *
 * <p>The two programs were conversational: each PF key re-entered the program, re-read the segment
 * chain from a key saved in the COMMAREA and re-sent the whole map. Here each screen action is one
 * stateless request that returns the data plus the keys needed to ask for the neighbouring page or
 * authorization, which is the modern replacement for the COMMAREA state required by Section 8.4.5.
 * The five rows per page, the ordering and the messages are unchanged.
 */
@Service
public class AuthorizationInquiryService {

    /** The five detail lines of map COPAU0A; PROCESS-PAGE-FORWARD loops WS-IDX from 1 to 5. */
    public static final int PAGE_SIZE = 5;

    private static final String APPROVED_RESPONSE_CODE = "00";

    private final CardholderLookupClient cardholderLookupClient;
    private final AuthorizationSummaryRepository summaryRepository;
    private final AuthorizationDetailRepository detailRepository;

    public AuthorizationInquiryService(CardholderLookupClient cardholderLookupClient,
                                       AuthorizationSummaryRepository summaryRepository,
                                       AuthorizationDetailRepository detailRepository) {
        this.cardholderLookupClient = cardholderLookupClient;
        this.summaryRepository = summaryRepository;
        this.detailRepository = detailRepository;
    }

    /**
     * GATHER-DETAILS with PROCESS-PAGE-FORWARD: the header from the account bounded context, the
     * totals from PAUTSUM0 and up to five children of that root.
     *
     * @param afterAuthKey the key of the last row of the previous page, absent for the first page
     */
    @Transactional(readOnly = true)
    public AuthorizationSummaryResponse summary(long accountId, String afterAuthKey) {
        CardholderView cardholder = requireCardholder(accountId);
        Optional<AuthorizationSummaryEntity> summary = summaryRepository.findById(accountId);

        // One row beyond the page decides whether a further page exists, which is what the extra
        // GNP of PROCESS-PAGE-FORWARD established before it set NEXT-PAGE-YES.
        List<AuthorizationDetailEntity> fetched = readChildren(accountId, afterAuthKey, PAGE_SIZE + 1);
        boolean morePages = fetched.size() > PAGE_SIZE;
        List<AuthorizationDetailEntity> page =
                fetched.subList(0, Math.min(PAGE_SIZE, fetched.size()));

        List<AuthorizationListRow> rows = new ArrayList<>(page.size());
        for (AuthorizationDetailEntity detail : page) {
            rows.add(toRow(detail));
        }

        String message = null;
        if (rows.isEmpty() && afterAuthKey != null) {
            message = AuthorizationMessages.BOTTOM_OF_PAGE;
        }

        return new AuthorizationSummaryResponse(
                accountId,
                cardholder.customerId(),
                customerName(cardholder),
                addressLine1(cardholder),
                addressLine2(cardholder),
                trim(cardholder.phone1()),
                MoneyValue.of(orZero(cardholder.creditLimit())),
                MoneyValue.of(orZero(cardholder.cashCreditLimit())),
                summary.map(s -> (int) orZero(s.getApprovedAuthCnt())).orElse(0),
                summary.map(s -> (int) orZero(s.getDeclinedAuthCnt())).orElse(0),
                MoneyValue.of(summary.map(AuthorizationSummaryEntity::getCreditBalance)
                        .orElse(BigDecimal.ZERO)),
                MoneyValue.of(summary.map(AuthorizationSummaryEntity::getCashBalance)
                        .orElse(BigDecimal.ZERO)),
                MoneyValue.of(summary.map(AuthorizationSummaryEntity::getApprovedAuthAmt)
                        .orElse(BigDecimal.ZERO)),
                MoneyValue.of(summary.map(AuthorizationSummaryEntity::getDeclinedAuthAmt)
                        .orElse(BigDecimal.ZERO)),
                summary.isPresent(),
                rows,
                rows.isEmpty() ? null : rows.get(0).authKey(),
                rows.isEmpty() ? null : rows.get(rows.size() - 1).authKey(),
                morePages,
                message);
    }

    /** READ-AUTH-RECORD plus READ-NEXT-AUTH-RECORD: one authorization and the key after it. */
    @Transactional(readOnly = true)
    public AuthorizationDetailResponse detail(long accountId, String authKey) {
        requireCardholder(accountId);
        AuthorizationDetailEntity detail = detailRepository
                .findById(new AuthorizationDetailId(accountId, authKey))
                .orElseThrow(() -> new RecordNotFoundException(
                        AuthorizationMessages.authorizationNotFound(authKey), Map.of()));

        List<AuthorizationDetailEntity> next = readChildren(accountId, authKey, 1);
        String nextAuthKey = next.isEmpty() ? null : next.get(0).getId().getAuthKey();
        String message = nextAuthKey == null ? AuthorizationMessages.LAST_AUTHORIZATION : null;
        return toDetail(detail, nextAuthKey, message);
    }

    /** Builds the detail response after a fraud toggle, carrying that action's message. */
    public AuthorizationDetailResponse detailWithMessage(AuthorizationDetailEntity detail,
                                                         String message) {
        List<AuthorizationDetailEntity> next =
                readChildren(detail.getId().getAcctId(), detail.getId().getAuthKey(), 1);
        return toDetail(detail, next.isEmpty() ? null : next.get(0).getId().getAuthKey(), message);
    }

    private List<AuthorizationDetailEntity> readChildren(long accountId, String afterAuthKey,
                                                         int limit) {
        PageRequest pageRequest = PageRequest.of(0, limit);
        return afterAuthKey == null
                ? detailRepository.findChildren(accountId, pageRequest)
                : detailRepository.findChildrenAfter(accountId, afterAuthKey, pageRequest);
    }

    /**
     * GETCARDXREF-BYACCT, GETACCTDATA-BYACCT and GETCUSTDATA-BYCUST. Each read had its own NOTFND
     * message, and the first miss is the one the screen showed.
     */
    private CardholderView requireCardholder(long accountId) {
        CardholderView cardholder = cardholderLookupClient.byAccountId(accountId);
        String accountIdText = Long.toString(accountId);
        if (!cardholder.cardFound()) {
            throw new RecordNotFoundException(
                    AuthorizationMessages.accountNotFoundInXref(accountIdText), Map.of());
        }
        if (!cardholder.accountFound()) {
            throw new RecordNotFoundException(
                    AuthorizationMessages.accountNotFoundInAcct(accountIdText), Map.of());
        }
        if (!cardholder.customerFound()) {
            throw new RecordNotFoundException(AuthorizationMessages.customerNotFound(
                    String.valueOf(cardholder.customerId())), Map.of());
        }
        return cardholder;
    }

    private AuthorizationListRow toRow(AuthorizationDetailEntity detail) {
        return new AuthorizationListRow(
                trim(detail.getId().getAuthKey()),
                trim(detail.getTransactionId()),
                displayDate(detail.getAuthOrigDate()),
                displayTime(detail.getAuthOrigTime()),
                trim(detail.getAuthType()),
                approvalStatus(detail),
                trim(detail.getMatchStatus()),
                MoneyValue.of(orZero(detail.getApprovedAmt())));
    }

    private AuthorizationDetailResponse toDetail(AuthorizationDetailEntity detail,
                                                 String nextAuthKey,
                                                 String message) {
        return new AuthorizationDetailResponse(
                detail.getId().getAcctId(),
                trim(detail.getId().getAuthKey()),
                trim(detail.getCardNum()),
                displayDate(detail.getAuthOrigDate()),
                displayTime(detail.getAuthOrigTime()),
                MoneyValue.of(orZero(detail.getApprovedAmt())),
                MoneyValue.of(orZero(detail.getTransactionAmt())),
                approvalStatus(detail),
                trim(detail.getAuthRespCode()),
                DeclineReason.describe(trim(detail.getAuthRespReason())),
                trim(detail.getProcessingCode()),
                trim(detail.getPosEntryMode()),
                trim(detail.getMessageSource()),
                trim(detail.getMerchantCategoryCode()),
                displayExpiry(detail.getCardExpiryDate()),
                trim(detail.getAuthType()),
                trim(detail.getTransactionId()),
                trim(detail.getMatchStatus()),
                displayFraud(detail),
                trim(detail.getMerchantName()),
                trim(detail.getMerchantId()),
                trim(detail.getMerchantCity()),
                trim(detail.getMerchantState()),
                trim(detail.getMerchantZip()),
                nextAuthKey,
                message);
    }

    /** POPULATE-AUTH-LIST: response code '00' is shown as A, anything else as D. */
    private static String approvalStatus(AuthorizationDetailEntity detail) {
        return APPROVED_RESPONSE_CODE.equals(trim(detail.getAuthRespCode())) ? "A" : "D";
    }

    /** {@code PA-AUTH-ORIG-DATE} is YYMMDD and the map shows MM/DD/YY. */
    private static String displayDate(String yymmdd) {
        String value = trim(yymmdd);
        if (value.length() < 6) {
            return "";
        }
        return value.substring(2, 4) + "/" + value.substring(4, 6) + "/" + value.substring(0, 2);
    }

    /** {@code PA-AUTH-ORIG-TIME} is HHMMSS and the map shows HH:MM:SS. */
    private static String displayTime(String hhmmss) {
        String value = trim(hhmmss);
        if (value.length() < 6) {
            return "";
        }
        return value.substring(0, 2) + ":" + value.substring(2, 4) + ":" + value.substring(4, 6);
    }

    /** POPULATE-AUTH-DETAILS shows the four digit expiry as MM/YY. */
    private static String displayExpiry(String mmyy) {
        String value = trim(mmyy);
        if (value.length() < 4) {
            return "";
        }
        return value.substring(0, 2) + "/" + value.substring(2, 4);
    }

    /**
     * POPULATE-AUTH-DETAILS: a confirmed or removed authorization shows the flag, a hyphen and the
     * report date; anything else shows a single hyphen.
     */
    private static String displayFraud(AuthorizationDetailEntity detail) {
        String status = trim(detail.getAuthFraud());
        if (FraudStatus.CONFIRMED.equals(status) || FraudStatus.REMOVED.equals(status)) {
            return status + "-" + trim(detail.getFraudRptDate());
        }
        return "-";
    }

    private static String customerName(CardholderView cardholder) {
        String middle = trim(cardholder.middleName());
        String middleInitial = middle.isEmpty() ? "" : middle.substring(0, 1);
        return (trim(cardholder.firstName()) + " " + middleInitial + " "
                + trim(cardholder.lastName())).trim();
    }

    private static String addressLine1(CardholderView cardholder) {
        return trim(cardholder.addressLine1()) + "," + trim(cardholder.addressLine2());
    }

    private static String addressLine2(CardholderView cardholder) {
        String zip = trim(cardholder.zip());
        return trim(cardholder.addressLine3()) + "," + trim(cardholder.stateCode()) + ","
                + (zip.length() > 5 ? zip.substring(0, 5) : zip);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static short orZero(Short value) {
        return value == null ? 0 : value;
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
