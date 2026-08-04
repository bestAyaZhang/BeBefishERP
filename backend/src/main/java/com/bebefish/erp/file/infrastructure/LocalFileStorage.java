package com.bebefish.erp.file.infrastructure;

import com.bebefish.erp.file.domain.FileStorage;
import com.bebefish.erp.file.domain.ImageUpload;
import com.bebefish.erp.file.domain.StoredFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {
    private final Path storageDirectory;

    @Autowired
    public LocalFileStorage(@Value("${ERP_UPLOAD_DIR:uploads}") String storageDirectory) {
        this(Path.of(storageDirectory));
    }

    public LocalFileStorage(Path storageDirectory) {
        this.storageDirectory = storageDirectory.toAbsolutePath().normalize();
    }

    @Override
    public StoredFile store(ImageUpload upload) {
        try {
            Files.createDirectories(storageDirectory);
            var extension = extension(upload.originalName());
            Path target;
            String storageName;
            do {
                storageName = UUID.randomUUID() + extension;
                target = storageDirectory.resolve(storageName).normalize();
            } while (!target.startsWith(storageDirectory) || Files.exists(target));
            Files.write(target, upload.content(), StandardOpenOption.CREATE_NEW);
            return new StoredFile(storageName, target.toString(), "/uploads/" + storageName);
        } catch (IOException exception) {
            throw new IllegalStateException("图片存储失败", exception);
        }
    }

    private String extension(String originalName) {
        if (originalName == null) {
            return "";
        }
        var index = originalName.lastIndexOf('.');
        return index < 0 ? "" : originalName.substring(index).toLowerCase();
    }
}
