package com.pages.controller;

import com.pages.service.MediaService;
import com.pages.util.MediaStorageLocation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.zip.DataFormatException;

@Slf4j
@Controller
@RequestMapping("/Media")
public class MediaController {


    private final MediaService mediaService;
    private final MediaStorageLocation mediaStorageLocation;

    public MediaController(MediaService mediaService, MediaStorageLocation mediaStorageLocation) {
        this.mediaService = mediaService;
        this.mediaStorageLocation = mediaStorageLocation;
    }


    @GetMapping("/{filename:..+}")
    @ResponseBody
    public ResponseEntity<Resource> serveFile(@PathVariable String filename) throws DataFormatException {
        Resource media = mediaService.getResource(mediaStorageLocation.getLocation()+"/"+filename);
        if(media == null){
            return ResponseEntity.notFound().build();
        }

        MediaType contentType = MediaTypeFactory.getMediaType(media)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment;filename=\""+media.getFilename()+"\"")
                .contentType(contentType).body(media);
    }
}

