package com.bebefish.erp.file.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UploadController {
    private final Path uploadDirectory;

    public UploadController(@Value("${ERP_UPLOAD_DIR:uploads}") String uploadDirectory) {
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    @GetMapping("/uploads/{storageName:.+}")
    public ResponseEntity<Resource> image(@PathVariable String storageName) throws IOException {
        var imagePath = uploadDirectory.resolve(storageName).normalize();
        if (!imagePath.startsWith(uploadDirectory) || !Files.isRegularFile(imagePath)) {
            return ResponseEntity.notFound().build();
        }

        var contentType = Files.probeContentType(imagePath);
        var mediaType = contentType == null
                ? MediaType.APPLICATION_OCTET_STREAM
                : MediaType.parseMediaType(contentType);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(new FileSystemResource(imagePath));
    }
}
