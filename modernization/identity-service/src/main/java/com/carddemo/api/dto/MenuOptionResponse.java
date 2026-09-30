package com.carddemo.api.dto;

/** One admin menu entry of app/cpy/COADM02Y.cpy (option number, name and target program). */
public record MenuOptionResponse(int optionNumber, String name, String program) {
}
