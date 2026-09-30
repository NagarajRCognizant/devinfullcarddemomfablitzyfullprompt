package com.carddemo.api;

import com.carddemo.api.dto.MenuOptionResponse;
import com.carddemo.service.AdminMenuService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The admin menu of app/cbl/COADM01C.cbl with the option table of app/cpy/COADM02Y.cpy. */
@RestController
@RequestMapping("/api/admin-menu")
public class AdminMenuController {

    private final AdminMenuService adminMenuService;

    public AdminMenuController(AdminMenuService adminMenuService) {
        this.adminMenuService = adminMenuService;
    }

    @GetMapping
    public ResponseEntity<List<MenuOptionResponse>> options() {
        return ResponseEntity.ok(adminMenuService.options());
    }

    /** PROCESS-ENTER-KEY: resolves the keyed option to the program the menu transfers control to. */
    @GetMapping("/selection")
    public ResponseEntity<MenuOptionResponse> select(@RequestParam String option) {
        return ResponseEntity.ok(adminMenuService.select(option));
    }
}
