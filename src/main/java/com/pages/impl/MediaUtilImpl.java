
package com.pages.impl;

import com.pages.exception.PhotoStorageException;
import com.pages.interfaces.MediaUtil;
import com.pages.util.MediaStorageLocation;
import com.pages.util.UtilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Slf4j
@Component
public class MediaUtilImpl implements MediaUtil {

    private final MediaStorageLocation storage;

    public MediaUtilImpl(MediaStorageLocation storage) {
        this.storage = storage;
    }

    @Override
    public void init() throws IOException {
        Files.createDirectories(storage.getRoot());
        Files.createDirectories(storage.getListingsDirectory());
        Files.createDirectories(storage.getProductCategoriesDirectory());
        Files.createDirectories(storage.getCompanyDirectory());
        Files.createDirectories(storage.getTemporaryDirectory());
        Files.createDirectories(storage.getDisputeDirectory());
    }

    @Override
    public void store(MultipartFile file, String relativeKey) {
        if (file == null || file.isEmpty()) {
            throw new PhotoStorageException("Cannot store an empty file");
        }

        Path destination = resolveSafe(relativeKey);

        try {
            Files.createDirectories(destination.getParent());

            try (InputStream input = file.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }

            log.debug("Stored media at {}", relativeKey);
        } catch (IOException e) {
            throw new PhotoStorageException("Failed to store media", e);
        }
    }

    @Override
    public void store(MultipartFile file) {
        String filename = UUID.randomUUID() + "-"
                + UtilService.formatMediaName(file.getOriginalFilename());

        store(file, "temporary/" + filename);
    }

    @Override
    public void store(MultipartFile file, Long id) {
        if (id == null || id <= 0) {
            throw new PhotoStorageException("Invalid listing ID");
        }

        String filename = UUID.randomUUID() + "-"
                + UtilService.formatMediaName(file.getOriginalFilename());

        store(file, storage.getListingImageKey(id, filename));
    }

    @Override
    public Stream<Path> loadAll(String directory) {
        Path directoryPath = (directory == null || directory.isBlank())
                ? storage.getRoot()
                : resolveSafe(directory);

        if (!Files.isDirectory(directoryPath)) {
            throw new PhotoStorageException(
                    "Media directory does not exist: " + directory
            );
        }

        try (Stream<Path> paths = Files.list(directoryPath)) {
            return paths.map(storage.getRoot()::relativize).toList().stream();
        } catch (IOException e) {
            throw new PhotoStorageException("Failed to list media", e);
        }
    }

    @Override
    public Path toPath(String relativeKey) {
        return resolveSafe(relativeKey);
    }

    @Override
    public Resource loadAsResource(String relativeKey) {
        try {
            Path path = resolveSafe(relativeKey);

            Resource resource = new UrlResource(path.toUri());

            if (resource.exists()
                    && resource.isReadable()
                    && Files.isRegularFile(path)) {
                return resource;
            }

            throw new PhotoStorageException(
                    "Could not read media: " + relativeKey
            );
        } catch (IOException e) {
            throw new PhotoStorageException(
                    "Could not read media: " + relativeKey, e
            );
        }
    }

    @Override
    public boolean delete(String relativeKey) throws IOException {
        return Files.deleteIfExists(resolveSafe(relativeKey));
    }

    @Override
    public void delete(List<String> relativeKeys) throws IOException {
        if (relativeKeys == null) {
            return;
        }

        for (String key : relativeKeys) {
            delete(key);
        }
    }

    private Path resolveSafe(String relativeKey) {
        if (relativeKey == null || relativeKey.isBlank()) {
            throw new PhotoStorageException("Media path is empty");
        }

        Path supplied = Path.of(relativeKey);

        if (supplied.isAbsolute()) {
            throw new PhotoStorageException(
                    "Absolute media paths are not allowed"
            );
        }

        Path root = storage.getRoot();
        Path resolved = root.resolve(supplied).normalize();

        if (!resolved.startsWith(root)) {
            throw new PhotoStorageException(
                    "Media path escapes storage root"
            );
        }

        return resolved;
    }
}
