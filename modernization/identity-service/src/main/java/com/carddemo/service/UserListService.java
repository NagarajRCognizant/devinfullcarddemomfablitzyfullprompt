package com.carddemo.service;

import com.carddemo.api.dto.UserListResponse;
import com.carddemo.api.dto.UserSummary;
import com.carddemo.cobol.CobolText;
import com.carddemo.domain.UserMessages;
import com.carddemo.persistence.entity.SecurityUserEntity;
import com.carddemo.persistence.repository.SecurityUserRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of PROCESS-PAGE-FORWARD, PROCESS-PAGE-BACKWARD, PROCESS-PF7-KEY and PROCESS-PF8-KEY of
 * app/cbl/COUSR00C.cbl.
 *
 * <p>The program browses USRSEC on its key and shows ten rows. Enter starts the browse at the user
 * id typed into the filter (or at the start of the file when it is blank) and reads forward from
 * there; PF8 restarts the browse at the last id shown and skips it; PF7 restarts at the first id
 * shown, skips it and reads backwards. Those three cases are the {@link Direction} values, so the
 * window is always positioned by key and never by a row offset.
 *
 * <p>The page number is carried by the caller exactly as CDEMO-CU00-PAGE-NUM was carried in the
 * COMMAREA: forward adds one, backward subtracts one and never goes below one.
 */
@Service
public class UserListService {

    /** USER-REC OCCURS 10 TIMES: the fixed number of rows of the list map. */
    public static final int PAGE_SIZE = 10;

    /** Which of the three browse entry points of COUSR00C the caller is performing. */
    public enum Direction {
        /** Enter: STARTBR at the filter value, first record included. */
        FIRST,
        /** PF8: STARTBR at the last id shown, that record skipped. */
        NEXT,
        /** PF7: STARTBR at the first id shown, reading backwards. */
        PREVIOUS
    }

    private final SecurityUserRepository users;

    public UserListService(SecurityUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserListResponse list(Direction direction, String key, int pageNumber) {
        return switch (direction) {
            case FIRST -> firstPage(key);
            case NEXT -> nextPage(key, pageNumber);
            case PREVIOUS -> previousPage(key, pageNumber);
        };
    }

    /**
     * Enter: {@code MOVE 0 TO CDEMO-CU00-PAGE-NUM} then PROCESS-PAGE-FORWARD, which reads the record
     * at the filter key first. A browse that cannot be started reports the NOTFND message of
     * STARTBR-USER-SEC-FILE.
     */
    private UserListResponse firstPage(String key) {
        List<SecurityUserEntity> window = users.findByUserIdGreaterThanEqualOrderByUserIdAsc(
                startKey(key), Limit.of(PAGE_SIZE + 1));
        if (window.isEmpty()) {
            return new UserListResponse(List.of(), null, null, 0, false,
                    UserMessages.LIST_NO_USERS_FOUND);
        }
        return forwardPage(window, 0);
    }

    /**
     * PF8 with CDEMO-CU00-NEXT-PAGE-FLG on: the browse skips the last id shown. An exhausted file
     * reports the message of PROCESS-PF8-KEY, which is what the screen shows when the flag is off.
     */
    private UserListResponse nextPage(String key, int pageNumber) {
        List<SecurityUserEntity> window = users.findByUserIdGreaterThanOrderByUserIdAsc(
                startKey(key), Limit.of(PAGE_SIZE + 1));
        if (window.isEmpty()) {
            return new UserListResponse(List.of(), null, null, Math.max(pageNumber, 1), false,
                    UserMessages.LIST_AT_BOTTOM);
        }
        return forwardPage(window, pageNumber);
    }

    /** PF7: below the first page the program refuses to move and keeps the page displayed. */
    private UserListResponse previousPage(String key, int pageNumber) {
        if (pageNumber <= 1) {
            return new UserListResponse(List.of(), null, null, Math.max(pageNumber, 1), true,
                    UserMessages.LIST_AT_TOP);
        }
        List<SecurityUserEntity> backwards = users.findByUserIdLessThanOrderByUserIdDesc(
                startKey(key), Limit.of(PAGE_SIZE));
        if (backwards.isEmpty()) {
            return new UserListResponse(List.of(), null, null, 1, true, UserMessages.LIST_AT_TOP);
        }
        List<SecurityUserEntity> page = new ArrayList<>(backwards);
        java.util.Collections.reverse(page);
        // PROCESS-PAGE-BACKWARD only decrements when a further record precedes the page.
        int newPage = page.size() < PAGE_SIZE ? 1 : Math.max(pageNumber - 1, 1);
        return new UserListResponse(summaries(page), userId(page.get(0)),
                userId(page.get(page.size() - 1)), newPage, true, null);
    }

    /**
     * The forward loop reads eleven records: ten fill the map and the eleventh only sets
     * CDEMO-CU00-NEXT-PAGE-FLG. Reaching end of file inside the page leaves the ENDFILE message of
     * READNEXT-USER-SEC-FILE on the screen.
     */
    private UserListResponse forwardPage(List<SecurityUserEntity> window, int pageNumber) {
        boolean nextPageAvailable = window.size() > PAGE_SIZE;
        List<SecurityUserEntity> page = nextPageAvailable ? window.subList(0, PAGE_SIZE) : window;
        return new UserListResponse(summaries(page), userId(page.get(0)),
                userId(page.get(page.size() - 1)), pageNumber + 1, nextPageAvailable,
                nextPageAvailable ? null : UserMessages.LIST_REACHED_BOTTOM);
    }

    /**
     * {@code IF USRIDINI = SPACES OR LOW-VALUES MOVE LOW-VALUES TO SEC-USR-ID}: a blank filter
     * starts the browse at the lowest key, which is the first record of the file.
     */
    private String startKey(String key) {
        return CobolText.isBlank(key) ? "" : UserTypes.key(key);
    }

    private List<UserSummary> summaries(List<SecurityUserEntity> page) {
        return page.stream()
                .map(user -> new UserSummary(userId(user), CobolText.trim(user.getFirstName()),
                        CobolText.trim(user.getLastName()), CobolText.trim(user.getUserType())))
                .toList();
    }

    private String userId(SecurityUserEntity user) {
        return UserTypes.normaliseId(user.getUserId());
    }
}
