package com.bookinghealthy.repository;

import com.bookinghealthy.model.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import com.bookinghealthy.model.TransactionType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    // Lấy lịch sử giao dịch của user (Sắp xếp mới nhất)
    List<WalletTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Tổng tiền theo loại giao dịch — dùng để ĐỐI SOÁT với bảng bookings trên dashboard admin.
     *
     * Sổ ví là bản ghi HOÀN TIỀN đầy đủ: refundToWallet được gọi ở mọi đường hoàn tiền. Nhưng nó
     * KHÔNG phải bản ghi doanh thu đầy đủ — payWithWallet chỉ chạy ở nhánh WALLET, nên VNPay và
     * chuyển khoản ngân hàng không bao giờ ghi vào đây. Vì vậy chỉ dùng nó làm kiểm toán ĐỘC LẬP
     * đặt cạnh con số của bookings, tuyệt đối không trộn vào phép tính tiền của dashboard.
     *
     * Trên DB dev hai bên đang lệch thật: sổ ví ghi hoàn 1.800.000đ (6 giao dịch) còn bookings chỉ
     * còn 1.500.000đ (5 dòng REFUNDED) — chính khoảng lệch đó là lý do dòng đối soát tồn tại.
     */
    @Query("SELECT COALESCE(SUM(w.amount), 0) FROM WalletTransaction w WHERE w.type = :type")
    BigDecimal sumAmountByType(@Param("type") TransactionType type);

    // Sổ ví phải giữ để đối soát; xem UserService.whyCannotDelete.
    long countByUserId(Long userId);
}