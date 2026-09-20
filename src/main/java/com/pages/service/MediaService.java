package com.pages.service;

import com.pages.controller.MediaController;
import com.pages.dto.MediaResponse;
import com.pages.exception.PhotoNotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.MvcUriComponentsBuilder;


import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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




    /*
Retrieves images metadata using inventory item id from repository and actual image from local directory
*/
    public MediaResponse getCategoryImage(Category category)  {
            Media media= mediaRepo.findByCategoryIdAndSortOrder(category.getId(), category.getSortOrder()).orElse(null);

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

    public void deleteAllByCategory(Long category) throws IOException{
       Media media= mediaRepo.findByCategoryId(category).orElse(null);

        if(media !=null){
            media.setCategory(null);
            mediaRepo.delete(media);
            mediaUtil.delete(media.getFileName());
        }

    }
}

