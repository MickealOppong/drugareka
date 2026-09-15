package com.pages.util;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class MediaStorageLocation {

    // Reads MEDIA_UPLOAD_DIR from Railway variables.
    // Falls back to your local home directory folder if the variable is missing (like on your laptop).
    @Value("${MEDIA_UPLOAD_DIR:#{systemProperties['user.home'] + '/App-media-dir'}}")
    private String uploadLocation;

    public String getLocation(){
        return uploadLocation;
    }


}
