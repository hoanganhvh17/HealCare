package com.bookinghealthy;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestTemplate;

import java.util.TimeZone;

@SpringBootApplication
@EnableAsync // <-- THÊM ANNOTATION NÀY
public class BookingHealthyApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookingHealthyApplication.class, args);
	}

	/**
	 * Mọi cách nói ngày giờ trong app đều là giờ Việt Nam ("Hôm nay là Thứ Bảy, ngày ..." tiêm vào
	 * prompt AI, mốc "khung giờ đã qua", hạn đăng ký ca khám). JVM chạy ở múi giờ khác (server UTC,
	 * container mặc định) sẽ lệch 7 tiếng và báo sai NGÀY cho khách sau 17:00.
	 */
	@PostConstruct
	public void setDefaultTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
	}

	/**
	 * RestTemplate dùng chung cho lớp AI (OpenRouter).
	 *
	 * BẮT BUỘC có timeout: mặc định của SimpleClientHttpRequestFactory là 0 = CHỜ VÔ HẠN. Các hàm gọi
	 * AI lại chạy trong @Transactional, nên một lần OpenRouter treo là giữ luôn connection của
	 * HikariCP — chỉ cần ~10 người chat cùng lúc là cả website (trang chủ, đăng nhập, đặt lịch)
	 * cùng chết, không riêng chatbot.
	 */
	@Bean
	public RestTemplate restTemplate() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(8_000);
		factory.setReadTimeout(25_000);
		return new RestTemplate(factory);
	}

}
