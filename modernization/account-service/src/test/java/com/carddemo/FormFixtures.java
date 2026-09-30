package com.carddemo;

import com.carddemo.api.dto.AccountUpdateForm;

/** The screen form fixtures of the account update screen. */
public final class FormFixtures {

    private FormFixtures() {
    }

    /** The keyed form that matches {@link TestFixtures#account()} and {@link TestFixtures#customer()}. */
    public static AccountUpdateForm form() {
        return new AccountUpdateForm(
                "11111111111",
                "Y",
                "1025.50",
                "5000.00",
                "1000.00",
                "2015", "03", "01",
                "2026", "03", "01",
                "2022", "03", "01",
                "120.00",
                "240.00",
                "GRP0000001",
                "100000001",
                "123", "45", "6789",
                "1980", "07", "04",
                "720",
                "John", "Q", "Public",
                "1 Main Street", "Apt 2", "New York",
                "NY", "10001", "USA",
                "212", "555", "1234",
                "646", "555", "9876",
                "NY-DL-12345",
                "1234567890",
                "Y");
    }
}
