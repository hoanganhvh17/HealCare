package com.bookinghealthy.service;

import com.bookinghealthy.model.User;
import com.bookinghealthy.model.WalletTransaction;

import java.math.BigDecimal;
import java.util.List;

public interface WalletService {
    // Hoàn tiền vào ví
    void refundToWallet(User user, BigDecimal amount, String description);

    // Thanh toán bằng ví (Trả về true nếu thành công, false nếu không đủ tiền)
    boolean payWithWallet(User user, BigDecimal amount, String description);

    // Lấy lịch sử giao dịch
    List<WalletTransaction> getHistory(Long userId);
}