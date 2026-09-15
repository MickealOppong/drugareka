package com.pages.interfaces;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public interface MediaUtil {

    void init() throws IOException;

    void store(MultipartFile files,Long id);
    void store(MultipartFile file,String username);
    void store(MultipartFile file);

    Stream<Path> loadAll(String filename);

    Path toPath(String filename);

    Resource loadAsResource(String filename);

    boolean delete(String file) throws IOException;
    void delete(List<String> files) throws IOException;
}
