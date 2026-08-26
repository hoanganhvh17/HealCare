package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.TransactionType;
import com.bookinghealthy.model.User;
import com.bookinghealthy.model.WalletTransaction;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.repository.WalletTransactionRepository;
import com.bookinghealthy.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WalletServiceImpl implements WalletService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletTransactionRepository transactionRepository;

    @Override
    @Transactional
    public void refundToWallet(User user, BigDecimal amount, String description) {
        if (user == null || amount == null) {
            // booking_price là cột nullable, nên một lịch thiếu giá từng làm NPE nổ ra giữa
            // luồng hủy lịch — sau khi trạng thái đã đổi nhưng trước khi ghi sổ.
            throw new IllegalArgumentException("Thiếu thông tin để hoàn tiền vào ví.");
        }

        // Cộng tiền bằng UPDATE nguyên tử; xem giải thích ở UserRepository.creditBalance.
        userRepository.creditBalance(user.getId(), amount);

        // Đối tượng `user` đang giữ số dư CŨ (câu UPDATE ở trên không đi qua nó). Nạp lại để
        // mọi thứ đọc user sau lời gọi này — kể cả principal trong phiên — không thấy số cũ.
        userRepository.findById(user.getId()).ifPresent(fresh -> user.setBalance(fresh.getBalance()));

        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setType(TransactionType.REFUND);
        tx.setDescription(description);
        transactionRepository.save(tx);
    }

    @Override
    @Transactional
    public boolean payWithWallet(User user, BigDecimal amount, String description) {
        if (user == null || amount == null) {
            return false;
        }

        // Kiểm số dư VÀ trừ tiền trong MỘT câu lệnh: điều kiện balance >= :amount nằm ngay
        // trong WHERE nên không có khe hở giữa lúc kiểm và lúc ghi. Trả về 0 dòng nghĩa là
        // không đủ tiền. Bản cũ đọc-rồi-ghi trong Java, hai request song song cùng lọt.
        int updated = userRepository.debitBalance(user.getId(), amount);
        if (updated == 0) {
            return false;
        }

        userRepository.findById(user.getId()).ifPresent(fresh -> user.setBalance(fresh.getBalance()));

        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setAmount(amount);
        tx.setType(TransactionType.PAYMENT);
        tx.setDescription(description);
        transactionRepository.save(tx);

        return true;
    }

    @Override
    public List<WalletTransaction> getHistory(Long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
}