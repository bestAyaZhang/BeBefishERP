package com.bebefish.erp.file.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.common.api.BusinessException;
import com.bebefish.erp.file.domain.FileAsset;
import com.bebefish.erp.file.domain.FileAssetRepository;
import com.bebefish.erp.file.domain.ImageUpload;
import com.bebefish.erp.file.infrastructure.LocalFileStorage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImageUploadServiceTest {
    @TempDir Path uploadDirectory;

    @Test
    void rejectsNonImageContentType() {
        var service = service();

        assertThatThrownBy(() -> service.upload(new ImageUpload(
                "quote.pdf", "application/pdf", new byte[]{1, 2, 3}
        )))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("IMAGE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void rejectsImageLargerThanTenMegabytes() {
        var bytes = pngBytesWithLength(10 * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service().upload(new ImageUpload("large.png", "image/png", bytes)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("IMAGE_TOO_LARGE"));
    }

    @Test
    void rejectsImageWhenDeclaredTypeDoesNotMatchFileSignature() {
        assertThatThrownBy(() -> service().upload(new ImageUpload(
                "glass.png", "image/png", jpegBytes()
        )))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("IMAGE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void rejectsImageWhenExtensionDoesNotMatchFileSignature() {
        assertThatThrownBy(() -> service().upload(new ImageUpload(
                "glass.jpg", "image/jpeg", pngBytesWithLength(8)
        )))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code()).isEqualTo("IMAGE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void savesAllowedImageWithGeneratedUniqueFileNameAndMetadata() throws IOException {
        var repository = new FakeFileAssetRepository();
        var service = new ImageUploadService(new LocalFileStorage(uploadDirectory), repository);

        var first = service.upload(new ImageUpload("玻璃杯.png", "image/png", pngBytesWithLength(16)));
        var second = service.upload(new ImageUpload("玻璃杯.png", "image/png", pngBytesWithLength(16)));

        assertThat(first.id()).isNotNull();
        assertThat(first.storageName()).endsWith(".png").isNotEqualTo(second.storageName());
        assertThat(Files.readAllBytes(uploadDirectory.resolve(first.storageName())))
                .isEqualTo(pngBytesWithLength(16));
        assertThat(repository.values).hasSize(2);
        assertThat(first.accessUrl()).isEqualTo("/uploads/" + first.storageName());
    }

    private ImageUploadService service() {
        return new ImageUploadService(new LocalFileStorage(uploadDirectory), new FakeFileAssetRepository());
    }

    private byte[] jpegBytes() {
        return new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0x00, 0x01};
    }

    private byte[] pngBytesWithLength(int length) {
        var bytes = new byte[Math.max(length, 8)];
        bytes[0] = (byte) 0x89;
        bytes[1] = 0x50;
        bytes[2] = 0x4e;
        bytes[3] = 0x47;
        bytes[4] = 0x0d;
        bytes[5] = 0x0a;
        bytes[6] = 0x1a;
        bytes[7] = 0x0a;
        return bytes;
    }

    private static final class FakeFileAssetRepository implements FileAssetRepository {
        private final List<FileAsset> values = new ArrayList<>();

        @Override
        public FileAsset save(FileAsset fileAsset) {
            var saved = new FileAsset(
                    (long) values.size() + 1, fileAsset.originalName(), fileAsset.storageName(),
                    fileAsset.storagePath(), fileAsset.accessUrl(), fileAsset.contentType(),
                    fileAsset.sizeBytes(), fileAsset.status()
            );
            values.add(saved);
            return saved;
        }
    }
}
