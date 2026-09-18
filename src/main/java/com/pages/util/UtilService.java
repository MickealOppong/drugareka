package com.pages.util;

import org.apache.catalina.Role;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Base64;
import java.util.Locale;

public class UtilService {

    public static String formatNameToSlug(String name) {

        if (name == null || name.isBlank()) {
            return "";
        }

        String slug = Normalizer
                .normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return slug
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    public static String formatRoleInput(String role) {

        String formatted = role.trim().toUpperCase();

        if (formatted.startsWith("ROLE_")) {
            return formatted;
        }

        return "ROLE_" + formatted;
    }

    public static String generateReceiptConfirmationToken() {
        byte[] randomBytes = new byte[32];

        SecureRandom secureRandom = new SecureRandom();
        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }


    public static String formatMediaName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }

        try {
            // 1. Decode URL parameters like "%20" into real spaces
            name = URLDecoder.decode(name, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Fallback if decoding fails
        }

        // 2. Clear diacritics / accents
        String media = Normalizer
                .normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return media
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("['’]", "")          //  THE EXACT FIX: Wipes apostrophes immediately (men's -> mens)
                .replaceAll("_", "-")            // Turn underscores into dashes
                .replaceAll("\\s+", "-")         // Turn real spaces into dashes
                .replaceAll("[^a-z0-9-\\.]", "") // Protect letters, numbers, dashes, and the extension dot
                .replaceAll("-+", "-")           // Collapse any duplicate dashes
                .replaceAll("^-|-$", "");        // Strip any accidental dashes from the edges
    }



}
