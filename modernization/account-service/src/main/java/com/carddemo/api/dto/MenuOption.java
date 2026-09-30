package com.carddemo.api.dto;

/**
 * One entry of the main menu table (app/cpy/COMEN02Y.cpy) restricted to the account management
 * capability, with the user type that may select it.
 */
public record MenuOption(int number, String name, String program, String userType, boolean accessible) {
}
