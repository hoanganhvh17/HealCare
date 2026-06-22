package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private BigDecimal amount; // Số tiền biến động (Có thể âm hoặc dương tùy cách xử lý, ở đây ta lưu giá trị tuyệt đối và dùng Type để phân biệt)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type; // PAYMENT / REFUND

    @Column(columnDefinition = "TEXT")
    private String description; // VD: "Thanh toán lịch #123"

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}