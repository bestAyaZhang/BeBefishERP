package com.bebefish.erp.file.infrastructure;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class UploadResourceConfiguration implements WebMvcConfigurer {
    private final String resourceLocation;

    public UploadResourceConfiguration(@Value("${ERP_UPLOAD_DIR:uploads}") String uploadDirectory) {
        resourceLocation = Path.of(uploadDirectory).toAbsolutePath().normalize().toUri().toString();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(resourceLocation);
    }
}
