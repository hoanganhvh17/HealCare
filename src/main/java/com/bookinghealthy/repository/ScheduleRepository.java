package com.bookinghealthy.repository;

import com.bookinghealthy.config.LeavePolicy;
import com.bookinghealthy.model.Schedule;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Ca khám định kỳ của bác sĩ, GẮN VỚI TỪNG TUẦN qua {@code weekStart} (xem {@link Schedule}).
 *
 * Mọi nơi cần biết "ngày X bác sĩ làm những ca nào" phải dùng {@link #findEffective} hoặc
 * {@link #findEffectiveOn} — đây là NGUỒN DUY NHẤT của luật chọn tuần. Gọi thẳng
 * {@code findByDoctorId} sẽ trộn lẫn lịch của mọi tuần với nhau.
 */
@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // Tìm tất cả các khung giờ làm việc của 1 bác sĩ (MỌI tuần) — chỉ dùng để quản trị/dọn dữ liệu.
    List<Schedule> findByDoctorId(Long doctorId);

    // === LỊCH THEO TUẦN ===

    List<Schedule> findByDoctorIdAndWeekStart(Long doctorId, LocalDate weekStart);

    /** Lịch định kỳ mặc định (chưa gắn tuần) — dữ liệu seed và lịch cũ. */
    List<Schedule> findByDoctorIdAndWeekStartIsNull(Long doctorId);

    void deleteByDoctorIdAndWeekStart(Long doctorId, LocalDate weekStart);

    /** Tuần đã đăng ký gần nhất KHÔNG muộn hơn {@code week}; null nếu chưa đăng ký tuần nào. */
    @Query("select max(s.weekStart) from Schedule s "
            + "where s.doctor.id = :doctorId and s.weekStart <= :week")
    LocalDate findLatestWeekStartUpTo(@Param("doctorId") Long doctorId, @Param("week") LocalDate week);

    /**
     * Ca khám THỰC SỰ có hiệu lực của một bác sĩ trong tuần chứa {@code date}, theo thứ tự:
     * <ol>
     *   <li>lịch đã đăng ký cho đúng tuần đó;</li>
     *   <li>nếu tuần đó chưa đăng ký: lịch của lần đăng ký gần nhất TRƯỚC đó (mẫu cũ được
     *       giữ nguyên cho tới khi có đăng ký mới — nên tuần hiện tại không đổi khi bác sĩ
     *       đăng ký cho tuần sau);</li>
     *   <li>cuối cùng là lịch định kỳ mặc định của dữ liệu seed.</li>
     * </ol>
     * Rỗng = bác sĩ chưa có lịch nào, phía đặt khám giữ hành vi cũ (không giới hạn).
     */
    default List<Schedule> findEffective(Long doctorId, LocalDate date) {
        LocalDate week = LeavePolicy.weekStartOf(date);

        List<Schedule> exact = findByDoctorIdAndWeekStart(doctorId, week);
        if (!exact.isEmpty()) {
            return exact;
        }

        LocalDate latest = findLatestWeekStartUpTo(doctorId, week);
        if (latest != null) {
            return findByDoctorIdAndWeekStart(doctorId, latest);
        }
        return findByDoctorIdAndWeekStartIsNull(doctorId);
    }

    /**
     * Bản nhiều bác sĩ của {@link #findEffective} cho trang lịch khám công khai: mọi ca khám
     * có hiệu lực trong ngày {@code date}, của tất cả bác sĩ. Gộp về 2 truy vấn thay vì
     * một truy vấn cho mỗi bác sĩ.
     */
    default List<Schedule> findEffectiveOn(LocalDate date) {
        LocalDate week = LeavePolicy.weekStartOf(date);

        // Tuần có hiệu lực của TỪNG bác sĩ phải tính trên cả 7 ngày, không lọc theo thứ trước:
        // một tuần đã đăng ký mà không có ca vào thứ đang xem nghĩa là hôm đó bác sĩ nghỉ,
        // chứ không phải rơi về mẫu của tuần cũ hơn.
        Map<Long, LocalDate> effectiveWeek = new HashMap<>();
        for (Object[] row : findLatestWeekStartPerDoctor(week)) {
            effectiveWeek.put((Long) row[0], (LocalDate) row[1]);
        }

        List<Schedule> result = new ArrayList<>();
        for (Schedule schedule : findCandidatesForDay(date.getDayOfWeek(), week)) {
            Long doctorId = schedule.getDoctor().getId();
            if (Objects.equals(schedule.getWeekStart(), effectiveWeek.get(doctorId))) {
                result.add(schedule);
            }
        }
        return result;
    }

    @Query("select s.doctor.id, max(s.weekStart) from Schedule s "
            + "where s.weekStart <= :week group by s.doctor.id")
    List<Object[]> findLatestWeekStartPerDoctor(@Param("week") LocalDate week);

    /** Lịch của một thứ, đã loại các tuần trong tương lai — còn lại do {@link #findEffectiveOn} lọc. */
    @EntityGraph(attributePaths = {"doctor", "doctor.user", "doctor.department"})
    @Query("select s from Schedule s "
            + "where s.dayOfWeek = :day and (s.weekStart is null or s.weekStart <= :week)")
    List<Schedule> findCandidatesForDay(@Param("day") DayOfWeek day, @Param("week") LocalDate week);
}
