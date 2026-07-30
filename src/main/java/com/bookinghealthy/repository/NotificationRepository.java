package com.bookinghealthy.repository;

import com.bookinghealthy.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Chuông chỉ hiện 20 thông báo mới nhất — phần cũ hơn không ai cuộn tới. */
    List<Notification> findTop20ByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    long countByRecipientIdAndReadFlagFalse(Long recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.readFlag = true "
            + "WHERE n.recipient.id = :recipientId AND n.readFlag = false")
    int markAllRead(@Param("recipientId") Long recipientId);
}
