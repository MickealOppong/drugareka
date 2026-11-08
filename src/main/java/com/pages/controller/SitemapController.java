package com.pages.controller;

import com.pages.service.SitemapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/sitemap.xml")
public class SitemapController {

    private final SitemapService sitemapService;

    @GetMapping(produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(sitemapService.generateSitemap());
    }
}