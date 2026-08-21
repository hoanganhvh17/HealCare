package com.bookinghealthy.config;

import com.bookinghealthy.service.FileStorageService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final FileStorageService fileStorage;
    private final AdminNavInterceptor adminNavInterceptor;

    public WebConfig(FileStorageService fileStorage, AdminNavInterceptor adminNavInterceptor) {
        this.fileStorage = fileStorage;
        this.adminNavInterceptor = adminNavInterceptor;
    }

    /**
     * Trạng thái active + huy hiệu của sidebar admin được bơm ở MỘT chỗ, thay vì rải
     * model.addAttribute("activePage", ...) qua 9 controller và ~29 điểm vào. Xem
     * {@link AdminNavInterceptor} để biết vì sao là interceptor chứ không phải @ControllerAdvice.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminNavInterceptor).addPathPatterns("/admin/**");
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
