package com.bookinghealthy.repository;

import com.bookinghealthy.model.AiChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {

    Optional<AiChatSession> findBySessionCode(String sessionCode);

    // Lấy lịch sử chat của User đã đăng nhập (sắp xếp mới nhất lên đầu)
    List<AiChatSession> findByUserIdOrderByUpdatedAtDesc(Long userId);

    // === CHIẾN LƯỢC 2: LỆNH XÓA RÁC TỰ ĐỘNG ===
    @Modifying
    @Query("DELETE FROM AiChatSession s WHERE s.user IS NULL AND s.updatedAt < :cutoffDate")
    void deleteGuestSessionsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
}