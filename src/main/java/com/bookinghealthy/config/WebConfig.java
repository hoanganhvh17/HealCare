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
    private final DoctorNavInterceptor doctorNavInterceptor;

    public WebConfig(FileStorageService fileStorage,
                     AdminNavInterceptor adminNavInterceptor,
                     DoctorNavInterceptor doctorNavInterceptor) {
        this.fileStorage = fileStorage;
        this.adminNavInterceptor = adminNavInterceptor;
        this.doctorNavInterceptor = doctorNavInterceptor;
    }

    /**
     * Trạng thái active + huy hiệu của sidebar admin được bơm ở MỘT chỗ, thay vì rải
     * model.addAttribute("activePage", ...) qua 9 controller và ~29 điểm vào. Xem
     * {@link AdminNavInterceptor} để biết vì sao là interceptor chứ không phải @ControllerAdvice.
     *
     * <p>{@link DoctorNavInterceptor} chỉ bơm HUY HIỆU (không đụng {@code activePage}, vốn đã được
     * từng controller bác sĩ tự đặt) và chỉ trên {@code /doctor/**} — khu lễ tân dùng sidebar riêng,
     * không có huy hiệu nào.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminNavInterceptor).addPathPatterns("/admin/**");
        registry.addInterceptor(doctorNavInterceptor).addPathPatterns("/doctor/**");
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
