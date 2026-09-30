package com.carddemo.api;

import com.carddemo.api.dto.UserActionResponse;
import com.carddemo.api.dto.UserAddForm;
import com.carddemo.api.dto.UserDetail;
import com.carddemo.api.dto.UserListResponse;
import com.carddemo.api.dto.UserUpdateForm;
import com.carddemo.service.UserAdminService;
import com.carddemo.service.UserListService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The four user administration screens: COUSR00C (list), COUSR01C (add), COUSR02C (update) and
 * COUSR03C (delete).
 *
 * <p>All four are reached from the admin menu, which COSGN00C only routes administrators to, so the
 * whole resource requires the administrator authority carried by the sign-on token.
 */
@RestController
@RequestMapping("/api/users")
public class UserAdminController {

    private final UserListService userListService;
    private final UserAdminService userAdminService;

    public UserAdminController(UserListService userListService, UserAdminService userAdminService) {
        this.userListService = userListService;
        this.userAdminService = userAdminService;
    }

    /**
     * COUSR00C: {@code direction=FIRST} with the filter value is Enter, {@code NEXT} with the last
     * id shown is PF8 and {@code PREVIOUS} with the first id shown is PF7.
     */
    @GetMapping
    public ResponseEntity<UserListResponse> list(
            @RequestParam(defaultValue = "FIRST") UserListService.Direction direction,
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int pageNumber) {
        return ResponseEntity.ok(userListService.list(direction, userId, pageNumber));
    }

    /** READ-USER-SEC-FILE of COUSR02C/COUSR03C: the record the screen displays before changing it. */
    @GetMapping("/{userId}")
    public ResponseEntity<UserDetail> find(@PathVariable String userId) {
        return ResponseEntity.ok(userAdminService.find(userId));
    }

    @PostMapping
    public ResponseEntity<UserActionResponse> add(@RequestBody UserAddForm form) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userAdminService.add(form));
    }

    @PutMapping("/{userId}")
    public ResponseEntity<UserActionResponse> update(@PathVariable String userId,
            @RequestBody UserUpdateForm form) {
        return ResponseEntity.ok(userAdminService.update(userId, form));
    }

    /** PF5 of COUSR03C. */
    @DeleteMapping("/{userId}")
    public ResponseEntity<UserActionResponse> delete(@PathVariable String userId) {
        return ResponseEntity.ok(userAdminService.delete(userId));
    }
}
