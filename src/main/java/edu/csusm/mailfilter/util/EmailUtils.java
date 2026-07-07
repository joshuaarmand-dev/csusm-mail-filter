package edu.csusm.mailfilter.util;

import java.util.Locale;

public class EmailUtils {
    private EmailUtils() {
    }

    public static String extractEmail(String fromHeader) {
        if (fromHeader == null) {
            return "";
        }

        int lt = fromHeader.indexOf('<');
        int gt = fromHeader.indexOf('>');

        String candidate = (lt >= 0 && gt > lt)
                ? fromHeader.substring(lt + 1, gt)
                : fromHeader;

        return candidate.trim();
    }

    public static boolean isCsusm(String email) {
        return email != null && email.toLowerCase(Locale.ROOT)
                .matches("^[^@]+@(?:[a-z0-9-]+\\.)*csusm\\.edu$");
    }

    public static String senderDomain(String email) {
        if (email == null) {
            return "";
        }

        int at = email.indexOf('@');

        return at > 0
                ? email.substring(at + 1).toLowerCase(Locale.ROOT)
                : "";
    }
}