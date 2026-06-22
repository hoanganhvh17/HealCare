package com.bookinghealthy.config; // Nhớ đổi tên package cho khớp với project của mày

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Lệnh này bảo Spring Boot: Nếu có ai gõ URL /uploads/...
        // thì hãy ra thư mục "uploads" nằm cạnh file .jar trên server để lấy ảnh cho nó xem.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }
}