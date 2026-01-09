package com.monitoring.logforwarder.util;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Utility class for date/time formatting and conversion operations.
 *
 * <p><b>Purpose:</b> Provides standardized date/time formatting using UTC timezone
 * and ISO-8601 patterns, plus conversion utilities between date types.</p>
 *
 * <p><b>Key Methods:</b></p>
 * <ul>
 *   <li>{@link #formatTimestamp} - Format LocalDateTime to ISO timestamp string</li>
 *   <li>{@link #parseTimestamp} - Parse ISO timestamp string to LocalDateTime</li>
 *   <li>{@link #toInstant} - Convert LocalDateTime to Instant</li>
 *   <li>{@link #now} - Get current UTC time</li>
 * </ul>
 *
 * @author Log Forwarder Team
 * @version 1.0
 * @since 1.0
 */
public class DateFormatUtil {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern(Constants.TIMESTAMP_FORMAT).withZone(ZoneId.of("UTC"));
    
    private static final DateTimeFormatter DATE_FORMATTER = 
        DateTimeFormatter.ofPattern(Constants.DATE_FORMAT);
    
    private static final DateTimeFormatter TIME_FORMATTER = 
        DateTimeFormatter.ofPattern(Constants.TIME_FORMAT);

    public static LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("UTC"));
    }

    public static String formatTimestamp(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(TIMESTAMP_FORMATTER);
    }

    public static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DATE_FORMATTER);
    }

    public static String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(TIME_FORMATTER);
    }

    public static LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(timestamp, TIMESTAMP_FORMATTER);
    }

    public static LocalDateTime parseDate(String dateString) {
        if (dateString == null || dateString.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateString + "T00:00:00", 
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    public static LocalDateTime parseDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeString);
        } catch (Exception e) {
            return null;
        }
    }

    public static long getDaysBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.DAYS.between(start, end);
    }

    public static long getHoursBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.HOURS.between(start, end);
    }

    public static long getMinutesBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.MINUTES.between(start, end);
    }

    public static long getSecondsBetween(LocalDateTime start, LocalDateTime end) {
        return ChronoUnit.SECONDS.between(start, end);
    }

    public static LocalDateTime addDays(LocalDateTime dateTime, long days) {
        return dateTime.plus(days, ChronoUnit.DAYS);
    }

    public static LocalDateTime addHours(LocalDateTime dateTime, long hours) {
        return dateTime.plus(hours, ChronoUnit.HOURS);
    }

    public static LocalDateTime addMinutes(LocalDateTime dateTime, long minutes) {
        return dateTime.plus(minutes, ChronoUnit.MINUTES);
    }

    public static LocalDateTime subtractDays(LocalDateTime dateTime, long days) {
        return dateTime.minus(days, ChronoUnit.DAYS);
    }

    public static LocalDateTime subtractHours(LocalDateTime dateTime, long hours) {
        return dateTime.minus(hours, ChronoUnit.HOURS);
    }

    public static LocalDateTime subtractMinutes(LocalDateTime dateTime, long minutes) {
        return dateTime.minus(minutes, ChronoUnit.MINUTES);
    }

    public static LocalDateTime getStartOfDay(LocalDateTime dateTime) {
        return dateTime.truncatedTo(ChronoUnit.DAYS);
    }

    public static LocalDateTime getEndOfDay(LocalDateTime dateTime) {
        return dateTime.truncatedTo(ChronoUnit.DAYS).plus(1, ChronoUnit.DAYS).minus(1, ChronoUnit.SECONDS);
    }

    public static Date localDateTimeToDate(LocalDateTime dateTime) {
        return java.sql.Timestamp.valueOf(dateTime);
    }

    public static LocalDateTime dateToLocalDateTime(Date date) {
        return date.toInstant().atZone(ZoneId.of("UTC")).toLocalDateTime();
    }

    public static boolean isInPast(LocalDateTime dateTime) {
        return dateTime.isBefore(now());
    }

    public static boolean isInFuture(LocalDateTime dateTime) {
        return dateTime.isAfter(now());
    }

    public static boolean isToday(LocalDateTime dateTime) {
        LocalDateTime today = now();
        return dateTime.toLocalDate().equals(today.toLocalDate());
    }

    public static boolean isYesterday(LocalDateTime dateTime) {
        LocalDateTime yesterday = now().minus(1, ChronoUnit.DAYS);
        return dateTime.toLocalDate().equals(yesterday.toLocalDate());
    }
}
