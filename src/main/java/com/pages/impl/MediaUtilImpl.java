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
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
public class MediaUtilImpl implements MediaUtil {

    private final Path root;


    public MediaUtilImpl(MediaStorageLocation mediaStorageLocation){
        if(mediaStorageLocation.getLocation().trim().isEmpty()){
            throw new PhotoStorageException("Could not initialize empty folder");
        }
        root = Paths.get(mediaStorageLocation.getLocation());

    }

    @Override
    public void init() throws IOException {
        if (!Files.exists(root)){
            Files.createDirectories(root);
        }
    }

    @Override
    public void store(MultipartFile file,String username) {
        try{
            if(file.isEmpty()){
                throw new PhotoStorageException("Failed to store empty file");
            }

            String formatedFilename = UtilService.formatMediaName(file.getOriginalFilename());

            Path destination = root.resolve(Paths.get(String.valueOf(username+"-"+formatedFilename))).normalize().toAbsolutePath();


            if(!destination.getParent().equals(this.root.toAbsolutePath())){
                throw new PhotoStorageException("File cannot be stored outside the current directory");
            }
            try(InputStream inputStream = file.getInputStream()){
                Files.copy(inputStream,destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }catch (IOException e){
            throw new PhotoStorageException("Failed to store file");
        }
    }
    @Override
    public void store(MultipartFile file) {

        try{
            if(file.isEmpty()){
                throw new PhotoStorageException("Failed to store empty file");
            }

            Path destination = root.resolve(Paths.get(String.valueOf(file.getOriginalFilename()))).normalize().toAbsolutePath();

            if(!destination.getParent().equals(this.root.toAbsolutePath())){
                throw new PhotoStorageException("File cannot be stored outside the current directory");
            }
            try(InputStream inputStream = file.getInputStream()){
                Files.copy(inputStream,destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }catch (IOException e){
            throw new PhotoStorageException("Failed to store file");
        }
    }

    @Override
    public void store(MultipartFile file,Long id) {
        try{
            if(file.isEmpty()){
                throw new PhotoStorageException("Failed to store empty file");
            }

            String formatedFilename = UtilService.formatMediaName(file.getOriginalFilename());

            Path destination = root.resolve(Paths.get(String.valueOf(id+"-"+formatedFilename))).normalize().toAbsolutePath();


            if(!destination.getParent().equals(this.root.toAbsolutePath())){
                throw new PhotoStorageException("File cannot be stored outside the current directory");
            }
            try(InputStream inputStream = file.getInputStream()){
                Files.copy(inputStream,destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }catch (IOException e){
            throw new PhotoStorageException("Failed to store file");
        }
    }

    @Override
    public Stream<Path> loadAll(String filename){
        try(var children = Files.walk(root,1)) {
            return children.filter(file->!file.equals(root)).map(
                    this.root::relativize);
        } catch (IOException e) {
            throw new PhotoStorageException("Failed to read files",e);
        }

    }


    @Override
    public Path toPath(String filename) {
        return Path.of(filename);
    }

    @Override
    public Resource loadAsResource(String filename) {
        try {
            Path file = toPath(filename);
            Resource resource = new UrlResource(file.toUri());
            if(resource.exists() || resource.isReadable()){
                return resource;
            }else{
                throw new PhotoStorageException("Count not read file "+filename);
            }
        } catch (MalformedURLException e) {
            throw new PhotoStorageException("Count not read file"+filename,e);
        }
    }

    @Override
    public boolean delete(String file) throws IOException {
        Path path = toPath(file);
        return Files.deleteIfExists(path);
    }

    @Override
    public void delete(List<String> files) throws IOException {
        for(String file:files){
            Path path = toPath(file);
             Files.deleteIfExists(path);
        }
    }
}
