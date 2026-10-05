package com.pages.service;

import com.pages.controller.MediaController;
import com.pages.dto.MediaResponse;
import com.pages.impl.MediaUtilImpl;
import com.pages.model.Category;
import com.pages.model.InventoryItem;
import com.pages.repository.MediaRepo;
import com.pages.util.Media;
import com.pages.util.MediaStorageLocation;
import com.pages.util.UtilService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;


import java.io.IOException;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
public class MediaService {

    private final MediaRepo mediaRepo;
    private final MediaUtilImpl mediaUtil;
    private final MediaStorageLocation mediaStorageLocation;


    public MediaService(MediaRepo mediaRepo, MediaUtilImpl mediaUtil, MediaStorageLocation mediaStorageLocation) {
        this.mediaRepo = mediaRepo;
        this.mediaUtil = mediaUtil;
        this.mediaStorageLocation = mediaStorageLocation;
    }


   @Transactional
    public void uploadCategoryImage(MultipartFile file,Category category) {
        // STEP 1: Calculate unique deterministic content hash fingerprint up-front
        String fileHash = calculateSHA256(file);
        log.info("has h{}",fileHash);

        // STEP 2: Check database registry index first to intercept duplicate uploads
        Optional<Media> existingAsset = mediaRepo.findByFileHash(fileHash);
        if (existingAsset.isPresent()) {
            log.info("Deduplication Shield: Match found for fingerprint [{}]. Skipping file storage write.", fileHash);
            return;
        }

        // STEP 3: Fall back to format and clean the file name if it's a net-new upload pass
        String sanitizedName = UtilService.formatMediaName(file.getOriginalFilename());
        // Append hash block string fragment to physical key file target path names to guarantee uniqueness
        String uniqueStorageKey = category.getId()+"-"+fileHash.substring(0, 8) + "-" + sanitizedName;


        // STEP 4: Persist the fingerprint mapping into the asset table manager registry

        Media media = Media.builder()
                .contentType(file.getContentType())
                .category(category)
                .fileName(uniqueStorageKey)
                .sortOrder(category.getSortOrder())
                .path(mediaStorageLocation.getLocation() +"/"+uniqueStorageKey)
                .fileHash(fileHash)
                .build();
          Media newMedia=  mediaRepo.save(media);
        mediaUtil.store(file, uniqueStorageKey);

    }


    @Transactional
    public void uploadProductImage(MultipartFile[] files, Integer[] sortOrders, InventoryItem item) {
        if (files == null || files.length == 0) return;

        for (int index = 0; index < files.length; index++) {
            MultipartFile file = files[index];
            if (file.isEmpty()) continue;

            String fileHash =calculateSHA256(file);

            Integer currentSortOrder = index;
            if (sortOrders != null && index < sortOrders.length && sortOrders[index] != null) {
                currentSortOrder = sortOrders[index];
            }

            //  See if this physical file content already exists on disk
            Optional<Media> existingAsset = mediaRepo.findByFileHash(fileHash);

            if (existingAsset.isPresent()) {
                Media duplicate = existingAsset.get();
                log.info("Deduplication Shield: Reusing physical disk file for hash [{}]. Creating pointer reference.", fileHash);
                continue;
            }

            // NET-NEW ASSET: Format and write new data blocks since this content hash doesn't exist anywhere yet
            String sanitizedName = UtilService.formatMediaName(file.getOriginalFilename());
            String uniqueStorageKey = item.getId()+"-"+fileHash.substring(0, 8) + "-" + sanitizedName;
            String computedStoragePath = mediaStorageLocation.getLocation() + "/" +uniqueStorageKey;

            Media newMedia = Media.builder()
                    .contentType(file.getContentType())
                    .inventoryItem(item)
                    .fileName(uniqueStorageKey)
                    .sortOrder(currentSortOrder)
                    .path(computedStoragePath)
                    .fileHash(fileHash)
                    .build();

            mediaRepo.save(newMedia);
            mediaUtil.store(file, uniqueStorageKey);
        }
    }

    public static String calculateSHA256(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot calculate fingerprint hash on an empty payload file container.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = file.getBytes();
            byte[] hashBytes = digest.digest(bytes);

            // Convert byte array into standard safe hexadecimal string sequence
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Logistics Engine: Failed to parse structural binary matrix for file hash generation.", e);
        }
    }


    public Resource getResource(String fileName){

        return mediaUtil.loadAsResource(fileName);
    }

    public void saveMedia(MultipartFile image, Category category) {
        try{
            String cleanFileName = category.getSlug()+"-"+Objects.requireNonNull(image.getOriginalFilename());

            Media media = Media.builder()
                    .contentType(image.getContentType())
                    .category(category)
                    .fileName(cleanFileName)
                    .sortOrder(category.getSortOrder())
                    .path(mediaStorageLocation.getLocation() +"/"+cleanFileName)
                    .build();
           Media savedMedia = mediaRepo.save(media);
            mediaUtil.store(image, category.getSlug());

        }catch (Exception ignored){

        }
    }

    public Media saveMedia(MultipartFile image) {
        try{
            Media media = Media.builder()
                    .contentType(image.getContentType())
                    .fileName(image.getOriginalFilename())
                    .path(mediaStorageLocation.getLocation() +"/"+ image.getOriginalFilename())
                    .build();
            Media savedMedia = mediaRepo.save(media);
            mediaUtil.store(image);
            return savedMedia;
        }catch (Exception e){
            return null;
        }
    }


    public void saveMedia(MultipartFile[] files,Integer[] sortOrders, InventoryItem item) {

            if (files.length==0 ) {
                throw new IllegalArgumentException("Empty file! Please upload again.");
            }

        IntStream.range(0,files.length).forEach(index->{
            MultipartFile file = files[index];

            Integer currentSortOrder = (sortOrders!=null && sortOrders.length>0?sortOrders[index]:index);

                String formatedFileName = UtilService.formatMediaName(item.getId()+"-"+file.getOriginalFilename());
            Media media = Media.builder()
                    .contentType(file.getContentType())
                    .inventoryItem(item)
                    .path(mediaStorageLocation.getLocation()+"/"+formatedFileName)
                    .fileName(formatedFileName)
                    .sortOrder(currentSortOrder)
                    .build();
            mediaRepo.save(media);
            mediaUtil.store(file,item.getId());
        });

    }

    @Transactional
    public void updateMedia(MultipartFile[] files, Integer[] sortOrders,InventoryItem inventoryItem) {

        List<Media> existing =
                mediaRepo.findAllByInventoryItemId(
                        inventoryItem.getId());

        existing.forEach(i->{
            log.info("exisitng images {}",i.getFileName());
        });

        // 1. Determine which existing images are still being used
        Set<String> incomingExistingName = Arrays.stream(files)
                .map(MultipartFile::getOriginalFilename)
                .map(UtilService::formatMediaName)
                .collect(Collectors.toSet());
       incomingExistingName.forEach(i->{
            log.info("incoming {}",i);
        });

        // 2. Delete images removed by seller
        existing.stream()
                .filter(media -> !incomingExistingName.contains(UtilService.formatMediaName(media.getFileName())))
                .forEach(media -> {

                    // Delete physical/cloud file here if necessary
                    mediaRepo.delete(media);

                    try {
                        mediaUtil.delete(media.getFileName());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });


        // 3. Update existing images / create new images
       for(int i=0; i<files.length;i++){
           MultipartFile request = files[i];

               Media media = existing.stream()
                       .filter(item ->
                               item.getFileName().equals(request.getOriginalFilename())
                       )
                       .findFirst()
                       .orElse(null);

               if(media!=null){
                   media.setSortOrder(i + 1);
                   mediaRepo.save(media);
               }else{
                   String formatedFileName = UtilService.formatMediaName(inventoryItem.getId()+"-"+request.getOriginalFilename());
                   Media newMedia = Media.builder()
                           .contentType(request.getContentType())
                           .inventoryItem(inventoryItem)
                           .path(mediaStorageLocation.getLocation()+"/"+formatedFileName)
                           .fileName(formatedFileName)
                           .sortOrder(i+1)
                           .build();
                   mediaRepo.save(newMedia);
                   mediaUtil.store(request,inventoryItem.getId());
               }

       }
    }


    @Transactional(rollbackOn = DuplicateKeyException.class)
    public void updateMediaFile(MultipartFile[] files, Integer[] sortOrders, InventoryItem inventoryItem) {
        if (files == null || files.length == 0) return;

        // Fetch all current database records linked to this listing item partition
        List<Media> existingMediaList = mediaRepo.findAllByInventoryItemId(inventoryItem.getId());

        //  Calculate the unique SHA-256 content hashes for ALL incoming files up-front
        List<String> incomingFileHashes = Arrays.stream(files)
                .map(file -> {
                    if (file.isEmpty()) return "";
                    return calculateSHA256(file);
                })
                .filter(hash -> !hash.isEmpty())
                .toList();

        //   Wipes old files that are no longer part of the incoming payload array
        existingMediaList.stream()
                .filter(media -> media.getFileHash() != null && !incomingFileHashes.contains(media.getFileHash()))
                .forEach(media -> {
                    log.warn("Deduplication Shield: Removing obsolete content hash asset [{}] from system", media.getFileHash());
                    mediaRepo.delete(media);
                    try {
                        mediaUtil.delete(media.getFileName()); // Cleans up physical file bytes from disk
                    } catch (IOException e) {
                        log.error("Storage Exception: Failed to wipe physical file matrix for key: " + media.getFileName(), e);
                    }
                });

        //  Iterates through files to filter out updates vs. net-new additions
        for (int i = 0; i < files.length; i++) {
            MultipartFile requestFile = files[i];
            if (requestFile.isEmpty()) continue;

            String currentFileHash = incomingFileHashes.get(i);

            // Safe dynamic extraction fallback for current image sort position order indices
            int assignedSortOrder = (sortOrders != null && i < sortOrders.length && sortOrders[i] != null)
                    ? sortOrders[i]
                    : (i + 1);

            // Check if this exact file content hash is already tracked in the database for this item
            Media matchingExistingMedia = existingMediaList.stream()
                    .filter(item -> item.getFileHash() != null && item.getFileHash().equals(currentFileHash))
                    .findFirst()
                    .orElse(null);

            if (matchingExistingMedia != null) {
                // 🟢 RULE MATCHED: File already exists in table! Skip file storage write, only refresh sorting order index weights
                log.info("Deduplication Shield: Content match found inside table for hash [{}]. Refreshing sorting index position.", currentFileHash);
                matchingExistingMedia.setSortOrder(assignedSortOrder);
                mediaRepo.save(matchingExistingMedia);
            } else {
                // 🚀 RULE MATCHED: Net-new unique content detected! Generate completely fresh isolated file instance blocks on disk
                String cleanName = UtilService.formatMediaName(requestFile.getOriginalFilename());
                String uniqueTargetName = inventoryItem.getId() + "-" + currentFileHash.substring(0, 8) + "-" + cleanName;
                String computedStoragePath = mediaStorageLocation.getLocation() + "/" + uniqueTargetName;

                log.info("Storing net-new isolated file asset [{}] with hash [{}]", uniqueTargetName, currentFileHash);

                Media newMedia = Media.builder()
                        .contentType(requestFile.getContentType())
                        .inventoryItem(inventoryItem)
                        .path(computedStoragePath)
                        .fileName(uniqueTargetName)
                        .sortOrder(assignedSortOrder)
                        .fileHash(currentFileHash) // Persists hash signature context safely
                        .build();

                mediaRepo.save(newMedia);

                // Pass the complete target file name to your disk writer tool to write new binary blocks
                mediaUtil.store(requestFile, uniqueTargetName);
            }
        }
    }






    @Transactional
    public void updateCategoryImage(MultipartFile file, Category category) {
        if (file == null || file.isEmpty()) return;

        // STEP 1: Calculate the unique SHA-256 fingerprint up-front
        String fileHash = calculateSHA256(file);

        // STEP 2: Check if this specific category already has an image record allocated
        Optional<Media> existingAsset = mediaRepo.findByCategory(category);

        if (existingAsset.isPresent()) {
            Media media = existingAsset.get();

            //  Exact content hash matches! No changes detected.
            if (media.getFileHash() != null && media.getFileHash().equals(fileHash)) {
                log.info("Deduplication Shield: Category [{}] image content is identical. Skipping storage write.", category.getName());
                return; // Fast-track exit!
            }

            //  Fresh image uploaded! Update the existing database row record in-place.
            log.info("Deduplication Shield: New image detected for Category [{}]. Overwriting existing asset row.", category.getName());

            String oldFileName = media.getFileName();

            // Format the net-new distinct storage key definitions safely
            String sanitizedName = UtilService.formatMediaName(file.getOriginalFilename());
            String uniqueStorageKey = fileHash.substring(0, 8) + "-" + sanitizedName;
            String computedStoragePath = mediaStorageLocation.getLocation() + "/" + uniqueStorageKey;

            // Mutate the properties of the existing record instead of calling mediaRepo.delete()
            media.setContentType(file.getContentType());
            media.setFileName(uniqueStorageKey);
            media.setPath(computedStoragePath);
            media.setFileHash(fileHash);
            media.setSortOrder(category.getSortOrder());

            mediaRepo.save(media);

            // Commit physical disk transitions safely
            mediaUtil.store(file, uniqueStorageKey);
            try {
                log.info("Storage Cleanup: Wiping obsolete physical file [{}] from system", oldFileName);
                mediaUtil.delete(oldFileName);
            } catch (IOException e) {
                log.error("Storage Exception: Failed to wipe old physical file matrix for key: " + oldFileName, e);
            }
            return; // Complete the update cycle successfully
        }

        //  Net-new category upload pass (No previous image record exists)
        log.info("Storing brand-new isolated asset image for Category: {}", category.getName());
        String sanitizedName = UtilService.formatMediaName(file.getOriginalFilename());
        String uniqueStorageKey = category.getId()+"-"+fileHash.substring(0, 8) + "-" + sanitizedName;
        String computedStoragePath = mediaStorageLocation.getLocation() + "/" + uniqueStorageKey;

        Media newMedia = Media.builder()
                .contentType(file.getContentType())
                .category(category)
                .fileName(uniqueStorageKey)
                .sortOrder(category.getSortOrder())
                .path(computedStoragePath)
                .fileHash(fileHash)
                .build();

        mediaRepo.save(newMedia);
        mediaUtil.store(file, uniqueStorageKey);
    }

    /*
Retrieves images metadata using inventory item id from repository and actual image from local directory
*/
    public MediaResponse getCategoryImage(Category category)  {
            Media media= mediaRepo.findByCategory(category).orElse(null);

            if(media!=null){

                return MediaResponse.builder()
                        .image( MvcUriComponentsBuilder.fromMethodName(
                                MediaController.class, "serveFile", media.getFileName()).build().toUri().toString())
                        .sortOrder(media.getSortOrder())
                        .id(media.getId())
                        .build();

        }
        return null;
    }

    public Media categoryImage(Category category)  {
       return mediaRepo.findByCategory(category).orElse(null);
    }

    public MediaResponse getListingMainImage(Long inventoryItemId)  {
        Media media= mediaRepo.findFirstByInventoryItemId(inventoryItemId).orElse(null);

        if(media!=null){

            return MediaResponse.builder()
                    .image( MvcUriComponentsBuilder.fromMethodName(
                            MediaController.class, "serveFile", media.getFileName()).build().toUri().toString())
                    .sortOrder(media.getSortOrder())
                    .id(media.getId())
                    .build();

        }
        return null;
    }

    public List<MediaResponse> getImageList(Long inventoryItemId)  {

        return mediaRepo.findAllByInventoryItemId(inventoryItemId)
                .stream().map(media->{
                    return MediaResponse.builder()
                            .image( MvcUriComponentsBuilder.fromMethodName(
                                    MediaController.class, "serveFile", media.getFileName()).build().toUri().toString())
                            .sortOrder(media.getSortOrder())
                            .id(media.getId())
                            .build();
                }).toList();

    }


    public void delete(Long id)throws IOException{
       Media media= mediaRepo.findById(id).orElse(null);
       if(media!=null) {
           //delete photo from database
           mediaRepo.delete(media);

           //check photo deleted
           boolean isDeleted= mediaRepo.findById(media.getId()).isPresent();
           if(!isDeleted){
               mediaUtil.delete(media.getPath());
           }
       }

    }

    private void deleteMediaFromDatabase(String path){
        mediaRepo.deleteByPath(path);
    }

    private void deleteMediaFromDatabase(List<String> path){
        for(String item:path){
            mediaRepo.deleteByPath(item);
        }
    }
    private void deleteMediaFromDatabase(InventoryItem item){
        mediaRepo.deleteByInventoryItem(item);
    }

    public void delete(String path)throws IOException {
        deleteMediaFromDatabase(path);
       mediaUtil.delete(path);

    }

    public void deleteAll(List<String> media) throws IOException{
       deleteMediaFromDatabase(media);
        mediaUtil.delete(media);

    }

    public void deleteAllByInventoryItem(InventoryItem item) throws IOException{
       List<Media> media= mediaRepo.findAllByInventoryItemId(item.getId());
        deleteMediaFromDatabase(item);
      for(Media image :media){
          mediaUtil.delete(image.getFileName());
      }

    }

    @Transactional
    public void deleteByCategory(Long category) throws IOException{
       Media media= mediaRepo.findByCategoryId(category).orElse(null);
        if(media !=null){
            media.setCategory(null);
            mediaRepo.delete(media);
            mediaRepo.flush();
            mediaUtil.delete(media.getFileName());
        }

    }
}

