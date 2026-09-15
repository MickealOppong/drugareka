package com.pages.util;

import com.pages.interfaces.ValidMedia;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Component
public class MediaFileValidator
        implements ConstraintValidator<ValidMedia, MultipartFile> {

    private static final long MAX_SIZE = 30L * 1024 * 1024;

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp",
            "video/mp4",
            "video/webm"
    );

    @Override
    public boolean isValid(
            MultipartFile file,
            ConstraintValidatorContext context) {

        if (file == null || file.isEmpty()) {
            return false;
        }

        if (file.getSize() > MAX_SIZE) {
            return false;
        }

        String contentType = file.getContentType();

        return contentType != null
                && ALLOWED_TYPES.contains(contentType.toLowerCase());
    }
}
