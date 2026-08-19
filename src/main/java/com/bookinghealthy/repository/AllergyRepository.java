package com.bookinghealthy.repository;

import com.bookinghealthy.model.Allergy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllergyRepository extends JpaRepository<Allergy, Long> {
    // Tìm tất cả các dị ứng của một bệnh nhân để cảnh báo Bác sĩ
    List<Allergy> findByUserId(Long userId);

    /** Danh sách cho bệnh nhân xem: mục vừa khai nằm trên cùng. */
    List<Allergy> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Chặn khai trùng một tác nhân. Không phân biệt hoa thường vì "Penicillin" và "penicillin"
     * là cùng một thứ, mà một danh sách cảnh báo có hai dòng giống nhau thì bác sĩ đọc lướt
     * sẽ tưởng là hai loại dị ứng khác nhau.
     */
    boolean existsByUserIdAndAllergenIgnoreCase(Long userId, String allergen);

    void deleteByUserId(Long userId);
}