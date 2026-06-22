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
        // 1. Cộng tiền
        user.setBalance(user.getBalance().add(amount));
        userRepository.save(user);

        // 2. Ghi lịch sử
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
        // 1. Kiểm tra số dư
        if (user.getBalance().compareTo(amount) < 0) {
            return false; // Không đủ tiền
        }

        // 2. Trừ tiền
        user.setBalance(user.getBalance().subtract(amount));
        userRepository.save(user);

        // 3. Ghi lịch sử
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