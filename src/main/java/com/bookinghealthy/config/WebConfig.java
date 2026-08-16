package com.bookinghealthy.config;

import com.bookinghealthy.service.FileStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FileStorageService fileStorage;

    public WebConfig(FileStorageService fileStorage) {
        this.fileStorage = fileStorage;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadLocation = fileStorage.uploadRoot().toUri().toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation);
        registry.addResourceHandler("/assets/img/health/**")
                .addResourceLocations(
                        uploadLocation + "health/",
                        "classpath:/static/assets/img/health/");
    }
}
