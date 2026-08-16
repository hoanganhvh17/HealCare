package com.bookinghealthy.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(100)
public class SchemaGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaGuard.class);

    private final JdbcTemplate jdbcTemplate;

    @Value("${app.schema.strict:false}")
    private boolean strict;

    public SchemaGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> missing = new ArrayList<>();

        if (!indexExists("bookings", "uk_bookings_slot")) {
            missing.add("index uk_bookings_slot trên bảng bookings — CHƯA CHỐNG ĐƯỢC ĐẶT TRÙNG KHUNG GIỜ giữa nhiều instance");
        }
        if (!indexExists("posts", "uk_posts_source_url")) {
            missing.add("index uk_posts_source_url trên bảng posts — tin tức tự thu thập có thể bị lưu trùng");
        }
        if (!tableExists("shedlock")) {
            missing.add("bảng shedlock — các job định kỳ sẽ chạy trên MỌI instance (email nhắc lịch gửi trùng)");
        }
        if (!tableExists("SPRING_SESSION")) {
            missing.add("bảng SPRING_SESSION (db/manual/002_spring_session.sql) — không đăng nhập được");
        }

        if (missing.isEmpty()) {
            log.info("[SchemaGuard] Lược đồ đầy đủ, đã chạy db/manual/001_prod_hardening.sql.");
            return;
        }

        StringBuilder message = new StringBuilder(
                "\n=====================================================================\n"
                        + "  LƯỢC ĐỒ CƠ SỞ DỮ LIỆU CÒN THIẾU — CHƯA CHẠY db/manual/001_prod_hardening.sql\n"
                        + "=====================================================================\n");
        for (String item : missing) {
            message.append("  ✗ ").append(item).append('\n');
        }
        message.append("---------------------------------------------------------------------\n")
                .append("  Chạy:  mysql -u <user> -p <database> < db/manual/001_prod_hardening.sql\n")
                .append("=====================================================================");

        if (strict) {
            throw new IllegalStateException(message.toString());
        }
        log.error(message.toString());
    }

    private boolean indexExists(String table, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.STATISTICS "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?",
                Integer.class, table, indexName);
        return count != null && count > 0;
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class, table);
        return count != null && count > 0;
    }
}
