package com.pages.util;

import com.pages.model.SellerShipmentToken;
import org.apache.catalina.Role;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

public class UtilService {

    public static String toPath( String parent,String category) {

        boolean isCategoryEmpty = category == null || category.trim().isBlank() || category.equalsIgnoreCase("null");
        boolean isParentEmpty = parent == null || parent.trim().isBlank() || parent.equalsIgnoreCase("null");

        if (isCategoryEmpty && isParentEmpty) {
            return null;
        }


        String sanitizedCategory = sanitizePathToken(category);

        if (isParentEmpty) {
            return "/" + sanitizedCategory;
        } else {
            // Standardize the active parent token string into a safe lowercase slug
            String sanitizedParent = sanitizePathToken(parent);
            return "/" + sanitizedParent + "/" + sanitizedCategory;
        }
    }


    /**
     *
     * Cleans up raw text variables into standard, web-compliant URL slug strings.
     * Example: "Major Home Appliances" ➔ "major-home-appliances"
     * Example: "Meble i Gabaryty"       ➔ "meble-gabaryty"
     */
    private static String sanitizePathToken(String token) {
        if (token == null) return "";

        return token.trim()
                .toLowerCase()
                // 1. Flatten Accent Diacritics (e.g., ł -> l, ó -> o)
                .replaceAll("[ąą]", "a")
                .replaceAll("[ćć]", "c")
                .replaceAll("[ęę]", "e")
                .replaceAll("[łł]", "l")
                .replaceAll("[ńń]", "n")
                .replaceAll("[óó]", "o")
                .replaceAll("[śś]", "s")
                .replaceAll("[źźżż]", "z")
                // 2. Remove filler connecting words common in marketplace categories
                .replaceAll("\\b(i|and|or)\\b", "")
                // 3. Strip remaining non-alphanumeric special characters
                .replaceAll("[^a-z0-9\\s-]", "")
                // 4. Compress multiple gaps or hyphens into a single clean dash separator
                .replaceAll("[\\s-]+", "-")
                // 5. Clean up any trailing/leading dashes left behind by stripped words
                .replaceAll("^-|-$", "");
    }

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
                .replaceAll("^-|-$", "")
                .replaceAll("-","_");
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

    public static SellerShipmentToken generateSellerShipmentToken(){
        return SellerShipmentToken.builder()
                .token(UUID.randomUUID().toString())
                .expiresAt(Instant.now().plus(3, ChronoUnit.DAYS))
                .build();
    }


        private static final Pattern PERFECT_FORMAT_PATTERN =
                Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*\\.[a-z0-9]+$");

        public static String formatMediaName(String name) {
            if (name == null || name.isBlank()) {
                return "";
            }

            //  format validation check
            String rawInput = name.trim();
            if (PERFECT_FORMAT_PATTERN.matcher(rawInput).matches()) {
                return rawInput; // Fast-track exit! Returns the string immediately with 0 parsing overhead.
            }

            try {
                // 2. Decode URL parameters like "%20" into real spaces safely
                name = URLDecoder.decode(name, StandardCharsets.UTF_8);
            } catch (Exception e) {
                // Fallback if decoding fails
            }

            // 3. / Polish accents safely (ł -> l, ó -> o, etc.)
            String normalized = Normalizer
                    .normalize(name, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "");

            // 4. Isolate the file extension to protect it from character stripping rules
            String baseName = normalized;
            String extension = "";
            int dotIndex = normalized.lastIndexOf('.');

            if (dotIndex > 0 && dotIndex < normalized.length() - 1) {
                baseName = normalized.substring(0, dotIndex);
                extension = normalized.substring(dotIndex).toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9.]", "");
            }

            // 5. Format ONLY the base file name strictly using your rules
            String cleanedBase = baseName
                    .toLowerCase(Locale.ROOT)
                    .trim()
                    .replaceAll("['’]", "")          // Wipes apostrophes immediately (men's -> mens)
                    .replaceAll("_", "-")            // Turn underscores into dashes
                    .replaceAll("\\s+", "-")         // Turn real spaces into dashes
                    .replaceAll("[^a-z0-9-]", "")    // Protect ONLY letters, numbers, and dashes
                    .replaceAll("-+", "-")           // Collapse any duplicate dashes
                    .replaceAll("^-|-$", "");        // Strip any accidental dashes from the base name edges cleanly

            // Reassemble the sanitized parts back together
            return cleanedBase + extension;

    }


}
