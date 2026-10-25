
package com.pages.service;

import com.pages.controller.MediaController;
import com.pages.dto.MediaResponse;
import com.pages.exception.PhotoStorageException;
import com.pages.impl.MediaUtilImpl;
import com.pages.model.Category;
import com.pages.model.InventoryItem;
import com.pages.model.OrderDispute;
import com.pages.repository.MediaRepo;
import com.pages.util.Media;
import com.pages.util.MediaStorageLocation;
import com.pages.util.UtilService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Slf4j
@Service
public class MediaService {

    private final MediaRepo mediaRepo;
    private final MediaUtilImpl mediaUtil;
    private final MediaStorageLocation storage;

    public MediaService(
            MediaRepo mediaRepo,
            MediaUtilImpl mediaUtil,
            MediaStorageLocation storage) {
        this.mediaRepo = mediaRepo;
        this.mediaUtil = mediaUtil;
        this.storage = storage;
    }

    // ---------------------------------------------------------
    // Category images
    // ---------------------------------------------------------

    @Transactional
    public void uploadCategoryImage(MultipartFile file, Category category) {
        updateCategoryImage(file, category);
    }

    @Transactional
    public void updateCategoryImage(MultipartFile file, Category category) {
        if (file == null || file.isEmpty()) {
            return;
        }

        String hash = calculateSHA256(file);

        Optional<Media> existing = mediaRepo.findByCategory(category);

        if (existing.isPresent()
                && hash.equals(existing.get().getFileHash())) {
            return;
        }

        String filename = UUID.randomUUID() + "-"
                + safeFilename(file.getOriginalFilename());

        String key = storage.getCategoryImageKey(category.getId(), filename);

        Media media = existing.orElseGet(Media::new);
        String oldKey = media.getPath();

        media.setCategory(category);
        media.setContentType(file.getContentType());
        media.setFileName(filename);
        media.setPath(key);
        media.setFileHash(hash);
        media.setSortOrder(category.getSortOrder());

        // Write the new file before switching the database record.
        mediaUtil.store(file, key);

        try {
            mediaRepo.saveAndFlush(media);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }

        if (oldKey != null && !oldKey.isBlank() && !oldKey.equals(key)) {
            deleteQuietly(oldKey);
        }
    }


    public void saveMedia(MultipartFile image, Category category) {
        updateCategoryImage(image, category);
    }

    public Media categoryImage(Category category) {
        return mediaRepo.findByCategory(category).orElse(null);
    }

    public MediaResponse getCategoryImage(Category category) {
        return mediaRepo.findByCategory(category)
                .map(this::toResponse)
                .orElse(null);
    }

    @Transactional
    public void deleteByCategory(Category category) throws IOException {
        Optional<Media> existing = mediaRepo.findFirstByCategory(category);

        if (existing.isEmpty()) {
            return;
        }

        Media media = existing.get();
        String key = media.getPath();

        mediaRepo.delete(media);
        mediaRepo.flush();

        deleteQuietly(key);
    }

    // ---------------------------------------------------------
    // Listing images
    // ---------------------------------------------------------

    @Transactional
    public void uploadProductImage(
            MultipartFile[] files,
            Integer[] sortOrders,
            InventoryItem item) {
        updateMediaFile(files, sortOrders, item);
    }


    @Transactional
    public void uploadReturnImage(
            MultipartFile[] files,
            Integer[] sortOrders,
            OrderDispute dispute) {
        updateMediaFile(files, sortOrders,dispute);
    }

    public void saveMedia(
            MultipartFile[] files,
            Integer[] sortOrders,
            InventoryItem item) {
        updateMediaFile(files, sortOrders, item);
    }

    @Transactional
    public void updateMedia(
            MultipartFile[] files,
            Integer[] sortOrders,
            InventoryItem item) {
        updateMediaFile(files, sortOrders, item);
    }

    @Transactional
    public void updateMediaFile(
            MultipartFile[] files,
            Integer[] sortOrders,
            InventoryItem item) {

        if (item == null || item.getId() == null) {
            throw new IllegalArgumentException(
                    "A persisted listing is required before uploading images"
            );
        }

        if (files == null) {
            files = new MultipartFile[0];
        }

        List<Media> existing =
                mediaRepo.findAllByInventoryItemId(item.getId());

        // Index the existing records by content hash.
        Map<String, Media> existingByHash = new HashMap<>();

        for (Media media : existing) {
            if (media.getFileHash() != null) {
                existingByHash.putIfAbsent(media.getFileHash(), media);
            }
        }

        Set<String> retainedHashes = new HashSet<>();
        List<String> obsoleteKeys = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];

            if (file == null || file.isEmpty()) {
                continue;
            }

            String hash = calculateSHA256(file);
            int sortOrder = resolveSortOrder(sortOrders, i);

            Media matching = existingByHash.get(hash);

            if (matching != null) {
                // The same image already belongs to THIS listing.
                matching.setSortOrder(sortOrder);
                mediaRepo.save(matching);
                retainedHashes.add(hash);
                continue;
            }

            String filename = UUID.randomUUID() + "-"
                    + safeFilename(file.getOriginalFilename());

            String key = storage.getListingImageKey(item.getId(), filename);

            Media newMedia = Media.builder()
                    .inventoryItem(item)
                    .contentType(file.getContentType())
                    .fileName(filename)
                    .path(key)
                    .fileHash(hash)
                    .sortOrder(sortOrder)
                    .build();

            // Write the image before inserting the reference.
            mediaUtil.store(file, key);

            try {
                mediaRepo.save(newMedia);
            } catch (RuntimeException e) {
                deleteQuietly(key);
                throw e;
            }

            retainedHashes.add(hash);
        }

        // Remove records for images no longer submitted.
        for (Media old : existing) {
            String hash = old.getFileHash();

            if (hash == null || !retainedHashes.contains(hash)) {
                obsoleteKeys.add(old.getPath());
                mediaRepo.delete(old);
            }
        }

        mediaRepo.flush();

        // Physical cleanup happens after database records are removed.
        // A cleanup failure leaves an orphan file, not a broken listing.
        for (String key : obsoleteKeys) {
            deleteQuietly(key);
        }
    }

    @Transactional
    public void updateMediaFile(
            MultipartFile[] files,
            Integer[] sortOrders,
            OrderDispute item) {

        if (item == null || item.getId() == null) {
            throw new IllegalArgumentException(
                    "A persisted return is required before uploading images"
            );
        }

        if (files == null) {
            files = new MultipartFile[0];
        }

        List<Media> existing = mediaRepo.findByOrderDispute(item);
        log.info("existing :{}",existing);

        // Index the existing records by content hash.
        Map<String, Media> existingByHash = new HashMap<>();

        for (Media media : existing) {
            if (media.getFileHash() != null) {
                existingByHash.putIfAbsent(media.getFileHash(), media);
            }
        }

        Set<String> retainedHashes = new HashSet<>();
        List<String> obsoleteKeys = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];

            if (file == null || file.isEmpty()) {
                continue;
            }

            String hash = calculateSHA256(file);
            int sortOrder = resolveSortOrder(sortOrders, i);

            Media matching = existingByHash.get(hash);

            if (matching != null) {
                // The same image already belongs to THIS listing.
                matching.setSortOrder(sortOrder);
                mediaRepo.save(matching);
                retainedHashes.add(hash);
                continue;
            }

            String filename = UUID.randomUUID() + "-"
                    + safeFilename(file.getOriginalFilename());

            String key = storage.getDisputeImageKey(item.getId(), filename);

            Media newMedia = Media.builder()
                    .orderDispute(item)
                    .contentType(file.getContentType())
                    .fileName(filename)
                    .path(key)
                    .fileHash(hash)
                    .sortOrder(sortOrder)
                    .build();



            try {
                mediaRepo.save(newMedia);
                // Write the image to file
                mediaUtil.store(file, key);
            } catch (RuntimeException e) {
                deleteQuietly(key);
                throw e;
            }

            retainedHashes.add(hash);
        }

        // Remove records for images no longer submitted.
        for (Media old : existing) {
            String hash = old.getFileHash();

            if (hash == null || !retainedHashes.contains(hash)) {
                obsoleteKeys.add(old.getPath());
                mediaRepo.delete(old);
            }
        }

        mediaRepo.flush();

        // Physical cleanup happens after database records are removed.
        // A cleanup failure leaves an orphan file, not a broken listing.
        for (String key : obsoleteKeys) {
            deleteQuietly(key);
        }
    }

    public MediaResponse getListingMainImage(Long inventoryItemId) {
        return mediaRepo.findFirstByInventoryItemId(inventoryItemId)
                .map(this::toResponse)
                .orElse(null);
    }

    public List<MediaResponse> getImageList(Long inventoryItemId) {
        return mediaRepo.findAllByInventoryItemId(inventoryItemId)
                .stream()
                .sorted(Comparator.comparing(
                        Media::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(this::toResponse)
                .toList();
    }
    public List<MediaResponse> getDisputeImageList(OrderDispute dispute) {
        return mediaRepo.findAllByOrderDispute(dispute)
                .stream()
                .sorted(Comparator.comparing(
                        Media::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .map(this::toResponse)
                .toList();
    }

    // ---------------------------------------------------------
    // Generic uploads
    // ---------------------------------------------------------

    public Media saveMedia(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new PhotoStorageException("Cannot store an empty image");
        }

        String filename = UUID.randomUUID() + "-"
                + safeFilename(image.getOriginalFilename());

        String key = storage.getCompanyImageKey(filename);

        Media media = Media.builder()
                .contentType(image.getContentType())
                .fileName(filename)
                .path(key)
                .fileHash(calculateSHA256(image))
                .build();

        mediaUtil.store(image, key);

        try {
            return mediaRepo.save(media);
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    public Resource getResource(String relativeKey) {
        return mediaUtil.loadAsResource(relativeKey);
    }

    // ---------------------------------------------------------
    // Deletion
    // ---------------------------------------------------------

    @Transactional
    public void delete(Long id) throws IOException {
        Optional<Media> result = mediaRepo.findById(id);

        if (result.isEmpty()) {
            return;
        }

        Media media = result.get();
        String key = media.getPath();

        mediaRepo.delete(media);
        mediaRepo.flush();

        deleteQuietly(key);
    }

    public void delete(String relativeKey) throws IOException {
        mediaRepo.deleteByPath(relativeKey);
        mediaUtil.delete(relativeKey);
    }

    public void deleteAll(List<String> relativeKeys) throws IOException {
        if (relativeKeys == null || relativeKeys.isEmpty()) {
            return;
        }

        for (String key : relativeKeys) {
            delete(key);
        }
    }

    @Transactional
    public void deleteAllByInventoryItem(InventoryItem item)
            throws IOException {

        if (item == null || item.getId() == null) {
            return;
        }

        List<Media> images =
                mediaRepo.findAllByInventoryItemId(item.getId());

        mediaRepo.deleteByInventoryItem(item);
        mediaRepo.flush();

        for (Media image : images) {
            deleteQuietly(image.getPath());
        }
    }

    // ---------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------

    private MediaResponse toResponse(Media media) {
        return MediaResponse.builder()
                .image(MvcUriComponentsBuilder
                        .fromMethodName(
                                MediaController.class,
                                "serveFile",
                                media.getPath())
                        .build()
                        .toUri()
                        .toString())
                .sortOrder(media.getSortOrder())
                .id(media.getId())
                .build();
    }

    private int resolveSortOrder(Integer[] sortOrders, int index) {
        if (sortOrders != null
                && index < sortOrders.length
                && sortOrders[index] != null) {
            return sortOrders[index];
        }

        return index + 1;
    }

    private String safeFilename(String originalFilename) {
        String formatted = UtilService.formatMediaName(
                originalFilename == null ? "image" : originalFilename
        );

        if (formatted == null || formatted.isBlank()) {
            formatted = "image";
        }

        // Keep only a filename, never a caller-supplied path.
        formatted = formatted.replace('\\', '/');
        formatted = formatted.substring(formatted.lastIndexOf('/') + 1);

        return formatted;
    }

    public static String calculateSHA256(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot calculate a hash for an empty file"
            );
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());

            StringBuilder result = new StringBuilder(hash.length * 2);

            for (byte value : hash) {
                result.append(String.format("%02x", value & 0xff));
            }

            return result.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "Failed to calculate image hash", e
            );
        }
    }

    private void deleteQuietly(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        try {
            mediaUtil.delete(key);
        } catch (Exception e) {
            log.error("Failed to delete media file {}", key, e);
        }
    }
}
