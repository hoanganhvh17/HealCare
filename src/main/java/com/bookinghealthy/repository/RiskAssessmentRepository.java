package com.bookinghealthy.repository;

import com.bookinghealthy.model.RiskAssessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiskAssessmentRepository extends JpaRepository<RiskAssessment, Long> {

    /**
     * Lịch sử của CHÍNH bác sĩ đang đăng nhập, mới nhất trước.
     * Có {@code ORDER BY} ngay trong tên method — đừng render danh sách chưa sắp xếp.
     */
    List<RiskAssessment> findTop20ByDoctorIdOrderByCreatedAtDesc(Long doctorId);

    /**
     * Vừa tra vừa gác quyền trong MỘT lời gọi: bản ghi không thuộc bác sĩ đang đăng nhập thì
     * trả rỗng y như khi nó không tồn tại. Controller đổi cả hai thành cùng một mã 404 nên
     * không dò được id của người khác.
     */
    Optional<RiskAssessment> findByIdAndDoctorId(Long id, Long doctorId);
}
