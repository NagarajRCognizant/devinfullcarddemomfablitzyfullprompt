package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.api.dto.MenuOption;
import com.carddemo.api.dto.MenuResponse;
import org.junit.jupiter.api.Test;

/** The menu access rule of COMEN01C, driven by the operator type carried in the sign-on token. */
class MenuAccessServiceTest {

    private final MenuAccessService service = new MenuAccessService();

    @Test
    void everyConvertedOptionIsOpenToARegularUser() {
        MenuResponse response = service.describe("USER0001", "U");

        assertThat(response.userType()).isEqualTo("U");
        assertThat(response.userTypeName()).isEqualTo("Regular user");
        assertThat(response.options()).allMatch(MenuOption::accessible);
        assertThat(response.options()).extracting(MenuOption::program)
                .containsExactly("COACTVWC", "COACTUPC", "COCRDLIC", "COCRDSLC", "COCRDUPC",
                        "COTRN00C", "COTRN01C", "COTRN02C", "CORPT00C", "COBIL00C", "COPAUS0C");
    }

    /** The option numbers are those of COMEN02Y. */
    @Test
    void theOptionsKeepTheNumbersOfTheSourceTable() {
        assertThat(service.describe("USER0001", "U").options()).extracting(MenuOption::number)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11);
    }

    @Test
    void anUnknownUserTypeDefaultsToRegular() {
        assertThat(service.describe("USER0001", " ").userType()).isEqualTo("U");
        assertThat(service.describe("USER0001", null).userType()).isEqualTo("U");
    }

    @Test
    void anAdministratorSeesTheSameOptions() {
        MenuResponse response = service.describe("admin001", "a");

        assertThat(response.userType()).isEqualTo("A");
        assertThat(response.userTypeName()).isEqualTo("Administrator");
        assertThat(response.options()).allMatch(MenuOption::accessible);
    }

    @Test
    void accessIsGrantedForBothProgramsAndBothUserTypes() {
        service.requireAccess("U", "COACTVWC");
        service.requireAccess("U", "COACTUPC");
        service.requireAccess("A", "COACTUPC");
        service.requireAccess("U", "COCRDLIC");
        service.requireAccess("U", "COCRDUPC");
    }

    @Test
    void anUnknownProgramIsARequestError() {
        assertThatThrownBy(() -> service.requireAccess("U", "COUSR00C"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
