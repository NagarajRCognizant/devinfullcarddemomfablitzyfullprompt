package com.carddemo.api.dto;

import java.util.List;

/** The locally selected test user, its type and the menu options that type may use. */
public record MenuResponse(String userId, String userType, String userTypeName, List<MenuOption> options) {
}
