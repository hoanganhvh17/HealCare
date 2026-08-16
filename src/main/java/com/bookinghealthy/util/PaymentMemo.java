package com.bookinghealthy.util;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PaymentMemo {

    private PaymentMemo() {
    }

    public static String format(String prefix, Long bookingId) {
        return prefix + " " + bookingId;
    }

    public static Optional<Long> parseBookingId(String prefix, String description) {
        if (prefix == null || prefix.isBlank() || description == null || description.isBlank()) {
            return Optional.empty();
        }
        Pattern pattern = Pattern.compile(
                Pattern.quote(prefix) + "\\s*0*(\\d{1,18})",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(description);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
