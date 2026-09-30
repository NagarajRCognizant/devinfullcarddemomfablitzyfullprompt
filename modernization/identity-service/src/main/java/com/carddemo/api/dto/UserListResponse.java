package com.carddemo.api.dto;

import java.util.List;

/**
 * One page of the COUSR00C list.
 *
 * <p>The program keeps CDEMO-CU00-USRID-FIRST, CDEMO-CU00-USRID-LAST, CDEMO-CU00-PAGE-NUM and
 * CDEMO-CU00-NEXT-PAGE-FLG in the COMMAREA to drive PF7/PF8; the same values are returned here so
 * the client can ask for the next or previous page. {@code message} carries the boundary text the
 * screen would show (top of page / bottom of page) or null when there is none.
 */
public record UserListResponse(List<UserSummary> users, String firstUserId, String lastUserId,
        int pageNumber, boolean nextPageAvailable, String message) {
}
