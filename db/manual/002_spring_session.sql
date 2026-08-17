-- =====================================================================
--  BẢNG LƯU PHIÊN ĐĂNG NHẬP (spring-session-jdbc)
-- =====================================================================
-- Chạy TAY một lần, sau 001_prod_hardening.sql:
--     mysql -u <user> -p bookinghealthy < db/manual/002_spring_session.sql
--
-- Nội dung COPY NGUYÊN VĂN từ org/springframework/session/jdbc/schema-mysql.sql trong
-- jar spring-session-jdbc 3.2.1. ĐỪNG viết tay lại: bố cục PRIMARY_ID/SESSION_ID và bộ
-- index ở đây là thứ mà JdbcIndexedSessionRepository truy vấn theo đúng tên.
--
-- Vì sao lưu phiên xuống DB: SecurityContext của Spring Security, `state` cùng
-- OAuth2AuthorizationRequest của luồng đăng nhập Google/Facebook, và FlashMap đằng sau mọi
-- RedirectAttributes đều nằm trong HttpSession của Tomcat. Ứng dụng không có
-- @ControllerAdvice nào, nên FlashMap là kênh báo lỗi DUY NHẤT — mất phiên là người dùng
-- thao tác hỏng mà không hiểu vì sao.
--
-- ĐÃ ĐỔI so với bản gốc: ATTRIBUTE_BYTES dùng MEDIUMBLOB thay cho BLOB.
-- BLOB của MySQL chỉ chứa được 64KB; SecurityContext kèm principal và
-- OAuth2AuthorizationRequest hoàn toàn có thể vượt mức đó, và khi vượt thì MySQL cắt cụt
-- dữ liệu — biểu hiện ra ngoài là người dùng bị đăng xuất ngẫu nhiên, rất khó lần ra.
-- =====================================================================

CREATE TABLE IF NOT EXISTS SPRING_SESSION (
    PRIMARY_ID            CHAR(36) NOT NULL,
    SESSION_ID            CHAR(36) NOT NULL,
    CREATION_TIME         BIGINT   NOT NULL,
    LAST_ACCESS_TIME      BIGINT   NOT NULL,
    MAX_INACTIVE_INTERVAL INT      NOT NULL,
    EXPIRY_TIME           BIGINT   NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID)
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;

CREATE UNIQUE INDEX SPRING_SESSION_IX1 ON SPRING_SESSION (SESSION_ID);
CREATE INDEX SPRING_SESSION_IX2 ON SPRING_SESSION (EXPIRY_TIME);
CREATE INDEX SPRING_SESSION_IX3 ON SPRING_SESSION (PRINCIPAL_NAME);

CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    MEDIUMBLOB   NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE=InnoDB ROW_FORMAT=DYNAMIC;

-- Kiểm tra:
SELECT TABLE_NAME FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('SPRING_SESSION', 'SPRING_SESSION_ATTRIBUTES');
