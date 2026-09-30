package com.carddemo.api.dto;

/** One USER-REC row of the COUSR00C list (user id, first name, last name, user type). */
public record UserSummary(String userId, String firstName, String lastName, String userType) {
}
