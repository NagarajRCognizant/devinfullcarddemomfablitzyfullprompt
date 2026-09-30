package com.carddemo.api.dto;

/** A USRSEC record as the update and delete screens display it, including the clear text password. */
public record UserDetail(String userId, String firstName, String lastName, String password,
        String userType) {
}
