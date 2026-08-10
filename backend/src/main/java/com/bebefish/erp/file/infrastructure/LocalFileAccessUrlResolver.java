package com.bebefish.erp.file.infrastructure;

import com.bebefish.erp.file.domain.FileAccessUrlResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "erp.file.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileAccessUrlResolver implements FileAccessUrlResolver {
    @Override
    public String resolve(String accessUrl) {
        return accessUrl;
    }
}
