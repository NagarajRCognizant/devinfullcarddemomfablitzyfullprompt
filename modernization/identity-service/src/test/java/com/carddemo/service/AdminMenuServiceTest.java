package com.carddemo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carddemo.domain.UserMessages;
import com.carddemo.exception.ScreenValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The option table of COADM02Y and the three rejections of COADM01C. */
class AdminMenuServiceTest {

    private final AdminMenuService service = new AdminMenuService();

    @Test
    void theMenuHoldsTheSixOptionsOfTheCopybook() {
        assertThat(service.options()).hasSize(6);
        assertThat(service.options().get(0).program()).isEqualTo("COUSR00C");
        assertThat(service.options().get(5).program()).isEqualTo("COTRTUPC");
    }

    @Test
    void aKeyedOptionResolvesToTheProgramTheMenuTransfersTo() {
        assertThat(service.select(" 3 ").program()).isEqualTo("COUSR02C");
    }

    @ParameterizedTest
    @ValueSource(strings = {"A", "0", "7", "99", ""})
    void aNonNumericZeroOrOutOfRangeOptionIsRejectedWithOneMessage(String option) {
        assertThatThrownBy(() -> service.select(option))
                .isInstanceOf(ScreenValidationException.class)
                .hasMessage(UserMessages.INVALID_MENU_OPTION);
    }
}
