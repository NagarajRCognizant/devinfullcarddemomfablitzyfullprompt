package com.carddemo.api.dto;

/** The outcome of one COCRDUPC turn: the card as it now stands plus the message the map showed. */
public record CardUpdateResponse(CardDetailResponse card, boolean updated, String message) {
}
