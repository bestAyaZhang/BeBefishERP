package com.bebefish.erp.file.application;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.file.domain.FileAsset;
import com.bebefish.erp.file.domain.FileAssetRepository;
import com.bebefish.erp.file.domain.FileStorage;
import com.bebefish.erp.file.domain.ImageUpload;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ImageUploadService {
    private static final long MAX_IMAGE_SIZE = 10L * 1024 * 1024;
    private static final Map<String, Set<String>> EXTENSIONS_BY_TYPE = Map.of(
            "image/jpeg", Set.of("jpg", "jpeg"),
            "image/png", Set.of("png"),
            "image/webp", Set.of("webp")
    );
    private final FileStorage storage;
    private final FileAssetRepository repository;

    public ImageUploadService(FileStorage storage, FileAssetRepository repository) {
        this.storage = storage;
        this.repository = repository;
    }

    public FileAsset upload(ImageUpload upload) {
        if (upload == null || upload.sizeBytes() > MAX_IMAGE_SIZE) {
            if (upload != null && upload.sizeBytes() > MAX_IMAGE_SIZE) {
                throw new BusinessException("IMAGE_TOO_LARGE", HttpStatus.BAD_REQUEST, "图片超过 10 MB");
            }
            throw invalidType();
        }
        var actualType = detectContentType(upload.content());
        var declaredType = normalized(upload.contentType());
        var extension = extension(upload.originalName());
        if (actualType == null || !actualType.equals(declaredType)
                || !EXTENSIONS_BY_TYPE.get(actualType).contains(extension)) {
            throw invalidType();
        }
        var stored = storage.store(upload);
        return repository.save(new FileAsset(
                null, upload.originalName(), stored.storageName(), stored.storagePath(), stored.accessUrl(),
                actualType, upload.sizeBytes(), "enabled"
        ));
    }

    private String detectContentType(byte[] content) {
        if (content.length >= 3 && (content[0] & 0xff) == 0xff
                && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        if (content.length >= 8 && (content[0] & 0xff) == 0x89 && content[1] == 0x50
                && content[2] == 0x4e && content[3] == 0x47 && content[4] == 0x0d
                && content[5] == 0x0a && content[6] == 0x1a && content[7] == 0x0a) {
            return "image/png";
        }
        if (content.length >= 12 && content[0] == 'R' && content[1] == 'I'
                && content[2] == 'F' && content[3] == 'F' && content[8] == 'W'
                && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private String normalized(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String extension(String value) {
        if (value == null) {
            return "";
        }
        var index = value.lastIndexOf('.');
        return index < 0 ? "" : value.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private BusinessException invalidType() {
        return new BusinessException("IMAGE_TYPE_NOT_ALLOWED", HttpStatus.BAD_REQUEST, "图片类型不支持");
    }
}
