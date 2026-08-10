package com.bebefish.erp.file.api;

import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.file.application.ImageUploadService;
import com.bebefish.erp.file.domain.FileAccessUrlResolver;
import com.bebefish.erp.file.domain.ImageUpload;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
public class FileController {
    private final ImageUploadService service;
    private final FileAccessUrlResolver urlResolver;

    public FileController(ImageUploadService service, FileAccessUrlResolver urlResolver) {
        this.service = service;
        this.urlResolver = urlResolver;
    }

    @PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('product:edit')")
    public ApiResponse<FileAssetResponse> uploadImage(@RequestPart("file") MultipartFile file)
            throws IOException {
        var asset = service.upload(new ImageUpload(
                file.getOriginalFilename(), file.getContentType(), file.getBytes()
        ));
        return ApiResponse.success(FileAssetResponse.from(asset, urlResolver));
    }
}
