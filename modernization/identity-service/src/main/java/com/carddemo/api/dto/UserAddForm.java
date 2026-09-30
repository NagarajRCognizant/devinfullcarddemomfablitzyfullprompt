package com.carddemo.api.dto;

/** The five input fields of the user add map (app/bms/COUSR01.bms). */
public record UserAddForm(String firstName, String lastName, String userId, String password,
        String userType) {
}
