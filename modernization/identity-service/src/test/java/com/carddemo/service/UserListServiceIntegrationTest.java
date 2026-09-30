package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.carddemo.PostgresIntegrationTest;
import com.carddemo.api.dto.UserListResponse;
import com.carddemo.api.dto.UserSummary;
import com.carddemo.domain.UserMessages;
import com.carddemo.service.UserListService.Direction;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The keyed browse of COUSR00C over the ten shipped records, which is exactly one full page, so the
 * page boundaries of the source are all reachable.
 */
class UserListServiceIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private UserListService service;

    @Test
    void aBlankFilterStartsAtTheFirstRecordAndFillsThePage() {
        UserListResponse page = service.list(Direction.FIRST, null, 0);

        assertThat(page.users()).hasSize(UserListService.PAGE_SIZE);
        assertThat(page.users()).first().extracting(UserSummary::userId).isEqualTo("ADMIN001");
        assertThat(page.firstUserId()).isEqualTo("ADMIN001");
        assertThat(page.lastUserId()).isEqualTo("USER0005");
        assertThat(page.pageNumber()).isEqualTo(1);
        assertThat(page.nextPageAvailable()).isFalse();
        assertThat(page.message()).isEqualTo(UserMessages.LIST_REACHED_BOTTOM);
    }

    /** A keyed filter positions the browse at or after that key. */
    @Test
    void aFilterStartsTheBrowseAtTheKeyedUserId() {
        UserListResponse page = service.list(Direction.FIRST, "USER0003", 0);

        assertThat(page.users()).extracting(UserSummary::userId)
                .containsExactly("USER0003", "USER0004", "USER0005");
        assertThat(page.message()).isEqualTo(UserMessages.LIST_REACHED_BOTTOM);
    }

    @Test
    void aFilterBeyondTheLastKeyReportsTheEmptyBrowse() {
        UserListResponse page = service.list(Direction.FIRST, "ZZZZZZZZ", 0);

        assertThat(page.users()).isEmpty();
        assertThat(page.message()).isEqualTo(UserMessages.LIST_NO_USERS_FOUND);
    }

    /** PF8 past the last id shown, with no records left, keeps the page and says so. */
    @Test
    void pagingForwardPastTheLastRecordReportsTheBottom() {
        UserListResponse page = service.list(Direction.NEXT, "USER0005", 1);

        assertThat(page.users()).isEmpty();
        assertThat(page.pageNumber()).isEqualTo(1);
        assertThat(page.message()).isEqualTo(UserMessages.LIST_AT_BOTTOM);
    }

    @Test
    void pagingForwardSkipsTheLastIdShown() {
        UserListResponse page = service.list(Direction.NEXT, "ADMIN003", 1);

        assertThat(page.users()).extracting(UserSummary::userId)
                .containsExactly("ADMIN004", "ADMIN005", "USER0001", "USER0002", "USER0003",
                        "USER0004", "USER0005");
        assertThat(page.pageNumber()).isEqualTo(2);
    }

    @Test
    void pagingBackFromTheFirstPageIsRefused() {
        UserListResponse page = service.list(Direction.PREVIOUS, "ADMIN001", 1);

        assertThat(page.users()).isEmpty();
        assertThat(page.message()).isEqualTo(UserMessages.LIST_AT_TOP);
    }

    /** PF7 reads backwards from the first id shown and restores the ascending order for the map. */
    @Test
    void pagingBackReadsTheRecordsBeforeTheFirstIdShownInKeyOrder() {
        UserListResponse page = service.list(Direction.PREVIOUS, "USER0003", 2);

        assertThat(page.users()).extracting(UserSummary::userId)
                .containsExactly("ADMIN001", "ADMIN002", "ADMIN003", "ADMIN004", "ADMIN005",
                        "USER0001", "USER0002");
        assertThat(page.pageNumber()).isEqualTo(1);
        assertThat(page.nextPageAvailable()).isTrue();
    }

    /** The forward window reads one record past the page to set CDEMO-CU00-NEXT-PAGE-FLG. */
    @Test
    void aFullPageWithMoreRecordsBehindItReportsAFurtherPage() {
        UserListResponse page = service.list(Direction.FIRST, null, 0);
        assertThat(page.nextPageAvailable()).isFalse();

        List<UserSummary> firstFive = page.users().subList(0, 5);
        assertThat(firstFive).extracting(UserSummary::userType).containsOnly("A");
    }
}
