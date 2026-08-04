package com.bebefish.erp.file.infrastructure;

import com.bebefish.erp.file.domain.FileAsset;
import com.bebefish.erp.file.domain.FileAssetRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class FileAssetJpaAdapter implements FileAssetRepository {
    private final JdbcTemplate jdbc;

    public FileAssetJpaAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public FileAsset save(FileAsset fileAsset) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "insert into file_asset (original_name, storage_name, storage_path, access_url, content_type, "
                            + "size_bytes, status, created_at, updated_at) "
                            + "values (?, ?, ?, ?, ?, ?, ?, now(3), now(3))",
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, fileAsset.originalName());
            statement.setString(2, fileAsset.storageName());
            statement.setString(3, fileAsset.storagePath());
            statement.setString(4, fileAsset.accessUrl());
            statement.setString(5, fileAsset.contentType());
            statement.setLong(6, fileAsset.sizeBytes());
            statement.setString(7, fileAsset.status());
            return statement;
        }, keyHolder);
        var key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("未返回文件主键");
        }
        return new FileAsset(
                key.longValue(), fileAsset.originalName(), fileAsset.storageName(), fileAsset.storagePath(),
                fileAsset.accessUrl(), fileAsset.contentType(), fileAsset.sizeBytes(), fileAsset.status()
        );
    }
}
