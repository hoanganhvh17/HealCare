package com.bookinghealthy.repository;

import com.bookinghealthy.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    // Lấy lịch sử giao dịch của user (Sắp xếp mới nhất)
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);
}