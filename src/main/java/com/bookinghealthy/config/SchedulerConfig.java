package com.bookinghealthy.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulerConfig {
    // File này đóng vai trò như một cái "Công tắc" an toàn.
    // Nó chỉ bật tính năng Scheduling (chạy ngầm) của Spring Boot
    // mà không can thiệp vào bất kỳ cấu hình gốc nào của dự án.
}