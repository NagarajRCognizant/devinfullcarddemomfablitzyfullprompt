package com.carddemo.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.carddemo.exception.ScreenValidationException;
import com.carddemo.persistence.entity.TransactionEntity;
import com.carddemo.persistence.repository.TransactionRepository;
import com.carddemo.transaction.api.dto.TransactionListResponse;
import com.carddemo.transaction.domain.TransactionMessages;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** COTRN00C, the transaction list and its two paging keys. */
@ExtendWith(MockitoExtension.class)
class TransactionListServiceTest {

    @Mock
    private TransactionRepository transactions;

    @InjectMocks
    private TransactionListService service;

    /** PROCESS-ENTER-KEY with no key entered browses from the start of the file. */
    @Test
    void theFirstPageBrowsesFromTheStartWhenNoIdentifierIsKeyed() {
        when(transactions.browseForwardFrom(eq(""), any())).thenReturn(page(1));
        when(transactions.browseForwardAfter(eq("0000000000000010"), any()))
                .thenReturn(List.of(TransactionFixtures.transaction("0000000000000011")));

        TransactionListResponse response = service.first(null);

        assertThat(response.rows()).hasSize(TransactionListService.PAGE_SIZE);
        assertThat(response.pageNumber()).isEqualTo(1);
        assertThat(response.firstTranId()).isEqualTo("0000000000000001");
        assertThat(response.lastTranId()).isEqualTo("0000000000000010");
        assertThat(response.nextPageAvailable()).isTrue();
        assertThat(response.message()).isEmpty();
    }

    /** The keyed identifier is padded to the file key before the browse starts. */
    @Test
    void aKeyedIdentifierPositionsTheBrowse() {
        when(transactions.browseForwardFrom(eq("0000000000000005"), any()))
                .thenReturn(List.of(TransactionFixtures.transaction("0000000000000005")));
        when(transactions.browseForwardAfter(eq("0000000000000005"), any())).thenReturn(List.of());

        TransactionListResponse response = service.first("5");

        assertThat(response.rows()).hasSize(1);
        assertThat(response.nextPageAvailable()).isFalse();
        assertThat(response.message()).isEqualTo(TransactionMessages.REACHED_BOTTOM);
    }

    @Test
    void aNonNumericIdentifierIsRejected() {
        assertThatThrownBy(() -> service.first("ABC"))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(TransactionMessages.TRAN_ID_NOT_NUMERIC);
    }

    /** PF8 without a remembered last key restarts the browse. */
    @Test
    void theNextPageWithoutALastKeyRestartsTheBrowse() {
        when(transactions.browseForwardFrom(eq(""), any())).thenReturn(page(1));
        when(transactions.browseForwardAfter(eq("0000000000000010"), any())).thenReturn(List.of());

        TransactionListResponse response = service.next("  ", 3);

        assertThat(response.pageNumber()).isEqualTo(1);
    }

    @Test
    void theNextPageStartsAfterTheLastKeyOfTheCurrentPage() {
        when(transactions.browseForwardAfter(eq("0000000000000010"), any())).thenReturn(page(11));
        when(transactions.browseForwardAfter(eq("0000000000000020"), any())).thenReturn(List.of());

        TransactionListResponse response = service.next("10", 1);

        assertThat(response.pageNumber()).isEqualTo(2);
        assertThat(response.firstTranId()).isEqualTo("0000000000000011");
        assertThat(response.message()).isEqualTo(TransactionMessages.REACHED_BOTTOM);
    }

    /** PF8 on the last page redisplays it with the already-at-the-bottom message. */
    @Test
    void theNextPageAtTheEndOfTheFileRedisplaysTheCurrentPage() {
        when(transactions.browseForwardAfter(eq("0000000000000010"), any())).thenReturn(List.of());
        when(transactions.browseBackwardBefore(eq("0000000000000010"), any()))
                .thenReturn(page(1).subList(0, 9).reversed());
        when(transactions.findById("0000000000000010"))
                .thenReturn(Optional.of(TransactionFixtures.transaction("0000000000000010")));

        TransactionListResponse response = service.next("10", 1);

        assertThat(response.pageNumber()).isEqualTo(1);
        assertThat(response.rows()).hasSize(TransactionListService.PAGE_SIZE);
        assertThat(response.message()).isEqualTo(TransactionMessages.ALREADY_AT_BOTTOM);
    }

    /** PF7 on the first page redisplays it with the already-at-the-top message. */
    @Test
    void thePreviousPageOnTheFirstPageRedisplaysIt() {
        when(transactions.browseForwardFrom(eq(""), any())).thenReturn(page(1));

        TransactionListResponse response = service.previous("", 1);

        assertThat(response.pageNumber()).isEqualTo(1);
        assertThat(response.message()).isEqualTo(TransactionMessages.ALREADY_AT_TOP);
    }

    /** A browse backwards that finds nothing leaves the user on the page they were on. */
    @Test
    void thePreviousPageWithNoEarlierRecordRedisplaysTheCurrentPage() {
        when(transactions.browseBackwardBefore(eq("0000000000000011"), any()))
                .thenReturn(List.of());
        when(transactions.browseForwardFrom(eq("0000000000000011"), any())).thenReturn(page(11));
        when(transactions.browseForwardAfter(eq("0000000000000020"), any())).thenReturn(List.of());

        TransactionListResponse response = service.previous("11", 2);

        assertThat(response.pageNumber()).isEqualTo(2);
        assertThat(response.message()).isEqualTo(TransactionMessages.ALREADY_AT_TOP);
    }

    /** The records read backwards are shown in ascending key order, as READPREV filled the map. */
    @Test
    void thePreviousPageIsShownInAscendingKeyOrder() {
        when(transactions.browseBackwardBefore(eq("0000000000000011"), any()))
                .thenReturn(page(1).reversed());
        when(transactions.browseForwardAfter(eq("0000000000000010"), any())).thenReturn(page(11));

        TransactionListResponse response = service.previous("11", 2);

        assertThat(response.pageNumber()).isEqualTo(1);
        assertThat(response.firstTranId()).isEqualTo("0000000000000001");
        assertThat(response.lastTranId()).isEqualTo("0000000000000010");
        assertThat(response.message()).isEqualTo(TransactionMessages.REACHED_TOP);
    }

    @Test
    void onlyTheSelectionFlagSIsAccepted() {
        assertThat(service.selected("s", "7")).isEqualTo("0000000000000007");
        assertThatThrownBy(() -> service.selected("X", "7"))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(TransactionMessages.INVALID_SELECTION);
    }

    private static List<TransactionEntity> page(int firstId) {
        return IntStream.range(0, TransactionListService.PAGE_SIZE)
                .mapToObj(index -> TransactionFixtures.transaction(
                        String.format("%016d", firstId + index)))
                .toList();
    }
}
