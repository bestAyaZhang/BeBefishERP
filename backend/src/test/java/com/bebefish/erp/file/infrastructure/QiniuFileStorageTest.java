package com.bebefish.erp.file.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bebefish.erp.file.domain.ImageUpload;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class QiniuFileStorageTest {
    @Test
    void createsSpringBeanWhenQiniuStorageIsEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(QiniuFileStorage.class)
                .withPropertyValues(
                        "erp.file.storage.provider=qiniu",
                        "QINIU_ACCESS_KEY=test-ak",
                        "QINIU_SECRET_KEY=test-sk",
                        "QINIU_BUCKET=test-bucket",
                        "QINIU_DOMAIN=img.example.com"
                )
                .run(context -> assertThat(context).hasSingleBean(QiniuFileStorage.class));
    }

    @Test
    void requiresProductionQiniuConfiguration() {
        assertThatThrownBy(() -> new QiniuFileStorage("", "", "", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QINIU_BUCKET");
    }

    @Test
    void storesImageInQiniuAndReturnsPublicUrl() {
        var client = new FakeQiniuObjectClient();
        var storage = new QiniuFileStorage(client, "https://img.example.com/");
        var content = new byte[]{1, 2, 3};

        var stored = storage.store(new ImageUpload("玻璃杯.png", "image/png", content));

        assertThat(stored.storageName()).endsWith(".png");
        assertThat(stored.storagePath()).isEqualTo(stored.storageName());
        assertThat(stored.accessUrl()).isEqualTo("https://img.example.com/" + stored.storageName());
        assertThat(client.uploads).singleElement().satisfies(upload -> {
            assertThat(upload.key()).isEqualTo(stored.storageName());
            assertThat(upload.content()).isEqualTo(content);
            assertThat(upload.contentType()).isEqualTo("image/png");
        });
    }

    private static final class FakeQiniuObjectClient implements QiniuObjectClient {
        private final List<Upload> uploads = new ArrayList<>();

        @Override
        public void put(byte[] content, String key, String contentType) {
            uploads.add(new Upload(content, key, contentType));
        }
    }

    private record Upload(byte[] content, String key, String contentType) {
    }
}
