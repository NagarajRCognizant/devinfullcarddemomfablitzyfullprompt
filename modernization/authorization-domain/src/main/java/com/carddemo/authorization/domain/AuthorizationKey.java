package com.carddemo.authorization.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoField;

/**
 * The composite child key of PAUTDTL1: {@code PA-AUTH-DATE-9C} followed by {@code PA-AUTH-TIME-9C},
 * both held as the nines complement of the value they carry.
 *
 * <p>IMS orders children by ascending key, and COPAUA0C paragraph 8500-INSERT-AUTH stores
 * {@code 99999 - yyddd} and {@code 999999999 - (hhmmss * 1000 + ms)} precisely so that the newest
 * authorization sorts first. The complement is therefore business logic - it defines the order the
 * summary screen pages through and the value CBPAUP0C reverses to recover the authorization date -
 * so it is reproduced here rather than replaced by a timestamp column with a descending index.
 */
public final class AuthorizationKey {

    /** The complement base of PA-AUTH-DATE-9C, a five digit Julian date. */
    public static final int DATE_COMPLEMENT_BASE = 99999;

    /** The complement base of PA-AUTH-TIME-9C, a nine digit time of day in milliseconds. */
    public static final long TIME_COMPLEMENT_BASE = 999_999_999L;

    private final int date9c;
    private final long time9c;

    public AuthorizationKey(int date9c, long time9c) {
        this.date9c = date9c;
        this.time9c = time9c;
    }

    /**
     * Builds the key COPAUA0C builds from the CICS ASKTIME/FORMATTIME pair: the five digit Julian
     * date and the time of day in milliseconds, each subtracted from its complement base.
     */
    public static AuthorizationKey of(LocalDateTime timestamp) {
        int yyddd = julianYyddd(timestamp.toLocalDate());
        LocalTime time = timestamp.toLocalTime();
        long hhmmss = time.getHour() * 10_000L + time.getMinute() * 100L + time.getSecond();
        long withMillis = hhmmss * 1000L + time.get(ChronoField.MILLI_OF_SECOND);
        return new AuthorizationKey(DATE_COMPLEMENT_BASE - yyddd, TIME_COMPLEMENT_BASE - withMillis);
    }

    /** Parses the fourteen character value the maps and the COMMAREA carry. */
    public static AuthorizationKey parse(String value) {
        if (value == null || value.trim().length() != 14 || !value.trim().chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Authorization key must be 14 digits");
        }
        String digits = value.trim();
        return new AuthorizationKey(Integer.parseInt(digits.substring(0, 5)),
                Long.parseLong(digits.substring(5)));
    }

    /** {@code WS-CUR-DATE-X6(1:5)}: the two digit year followed by the day of the year. */
    public static int julianYyddd(LocalDate date) {
        return (date.getYear() % 100) * 1000 + date.getDayOfYear();
    }

    /** The reverse of the date complement, as CBPAUP0C paragraph 3100-CHECK-AUTH-EXPIRY computes it. */
    public int julianDate() {
        return DATE_COMPLEMENT_BASE - date9c;
    }

    /** The reverse of the time complement: the time of day in milliseconds. */
    public long timeOfDayMillis() {
        return TIME_COMPLEMENT_BASE - time9c;
    }

    /**
     * The authorization timestamp COPAUS2C rebuilds in paragraph FORMAT-AUTH-TIMESTAMP before it
     * writes the DB2 fraud row, in the century window the source assumes for a two digit year.
     */
    public LocalDateTime toTimestamp(int centuryBase) {
        int julian = julianDate();
        LocalDate date = LocalDate.ofYearDay(centuryBase + julian / 1000, julian % 1000);
        long millis = timeOfDayMillis();
        long hhmmss = millis / 1000;
        LocalTime time = LocalTime.of((int) (hhmmss / 10_000), (int) (hhmmss / 100 % 100),
                (int) (hhmmss % 100), (int) (millis % 1000) * 1_000_000);
        return LocalDateTime.of(date, time);
    }

    /**
     * The AUTH_TS value COPAUS2C passes to {@code TIMESTAMP_FORMAT(:AUTH-TS, 'YY-MM-DD
     * HH24.MI.SSNNNNNN')}: the date part comes from PA-AUTH-ORIG-DATE as stored, the time part from
     * the uncomplemented PA-AUTH-TIME-9C. The two digit year is resolved into the 2000s, which is
     * the window the surrounding CardDemo data uses; a pre-2000 authorization date would need the
     * source DB2 subsystem's own windowing rule and is recorded as a review item.
     */
    public static LocalDateTime fraudTimestamp(String origDateYymmdd, long timeWithMillis) {
        String digits = origDateYymmdd == null ? "" : origDateYymmdd.trim();
        if (digits.length() != 6 || !digits.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("Authorization origin date must be YYMMDD");
        }
        LocalDate date = LocalDate.of(2000 + Integer.parseInt(digits.substring(0, 2)),
                Integer.parseInt(digits.substring(2, 4)), Integer.parseInt(digits.substring(4, 6)));
        long hhmmss = timeWithMillis / 1000;
        LocalTime time = LocalTime.of((int) (hhmmss / 10_000), (int) (hhmmss / 100 % 100),
                (int) (hhmmss % 100), (int) (timeWithMillis % 1000) * 1_000_000);
        return LocalDateTime.of(date, time);
    }

    public int date9c() {
        return date9c;
    }

    public long time9c() {
        return time9c;
    }

    /** The fourteen character value stored in the segment key and shown in CDEMO-CPVS-PAU-SELECTED. */
    public String value() {
        return "%05d%09d".formatted(date9c, time9c);
    }

    @Override
    public String toString() {
        return value();
    }
}
