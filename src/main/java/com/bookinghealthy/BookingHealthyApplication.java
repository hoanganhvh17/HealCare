package com.bookinghealthy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
@EnableAsync // <-- THÊM ANNOTATION NÀY
public class BookingHealthyApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookingHealthyApplication.class, args);
	}

	// Thêm đoạn này để dùng RestTemplate ở mọi nơi
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

}
