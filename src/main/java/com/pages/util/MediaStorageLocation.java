
package com.pages.util;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Getter
@Component
public class MediaStorageLocation {

    private final Path root;

    public MediaStorageLocation(
            @Value("${MEDIA_UPLOAD_DIR:#{systemProperties['user.home'] + '/App-media-dir'}}")
            String uploadLocation) {

        this.root = Path.of(uploadLocation)
                .toAbsolutePath()
                .normalize();
    }

    // Kept for compatibility with existing code that expects a String.
    public String getLocation() {
        return root.toString();
    }

    public Path getListingsDirectory() {
        return root.resolve("listings");
    }

    public Path getProductCategoriesDirectory() {
        return root.resolve("product-categories");
    }

    public Path getDisputeDirectory() {
        return root.resolve("order-disputes");
    }

    public Path getCompanyDirectory() {
        return root.resolve("company");
    }

    public Path getTemporaryDirectory() {
        return root.resolve("temporary");
    }

    public String getListingImageKey(Long listingId, String filename) {
        if (listingId == null || listingId <= 0) {
            throw new IllegalArgumentException("Invalid listing ID");
        }

        String shared = String.format("%02d", listingId % 100);

        return Path.of(
                "listings",
                shared,
                listingId.toString(),
                filename
        ).toString().replace('\\', '/');
    }

    public String getCategoryImageKey(Long categoryId, String filename) {
        if (categoryId == null || categoryId <= 0) {
            throw new IllegalArgumentException("Invalid category ID");
        }

        return Path.of(
                "product-categories",
                categoryId.toString(),
                filename
        ).toString().replace('\\', '/');
    }

    public String getCompanyImageKey(String filename) {
        return Path.of("company", filename)
                .toString()
                .replace('\\', '/');
    }
    public String getDisputeImageKey(Long itemId, String filename) {
        if (itemId == null || itemId<= 0) {
            throw new IllegalArgumentException("Invalid return ID");
        }

        String shared = String.format("%02d", itemId % 100);

        return Path.of(
                "order-disputes",
                shared,
                itemId.toString(),
                filename
        ).toString().replace('\\', '/');
    }
}
