package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.User;
import com.bookinghealthy.model.StaffShift;
import com.bookinghealthy.repository.AiChatSessionRepository;
import com.bookinghealthy.repository.AllergyRepository;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.LeaveRequestRepository;
import com.bookinghealthy.repository.NotificationRepository;
import com.bookinghealthy.repository.PostRepository;
import com.bookinghealthy.repository.ShiftCoverRequestRepository;
import com.bookinghealthy.repository.StaffProfileRepository;
import com.bookinghealthy.repository.StaffShiftRepository;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.repository.WalletTransactionRepository;
import org.springframework.transaction.annotation.Transactional;
import com.bookinghealthy.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List; // <-- THÊM IMPORT NÀY
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired private DoctorRepository doctorRepository;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private WalletTransactionRepository walletTransactionRepository;
    @Autowired private PostRepository postRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private StaffShiftRepository staffShiftRepository;
    @Autowired private ShiftCoverRequestRepository shiftCoverRequestRepository;
    @Autowired private AllergyRepository allergyRepository;
    @Autowired private AiChatSessionRepository aiChatSessionRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private StaffProfileRepository staffProfileRepository;

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    // === THÊM PHƯƠNG THỨC MỚI NÀY ===
    @Override
    public List<User> findAll() {
        return userRepository.findAll();
    }

    // === THÊM 3 PHƯƠNG THỨC MỚI NÀY ===
    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public User save(User user) {
        // (Chúng ta sẽ thêm logic mã hóa mật khẩu ở đây sau)
        return userRepository.save(user);
    }

    @Override
    public void deleteById(Long id) {
        userRepository.deleteById(id);
    }

    // === THÊM PHƯƠNG THỨC MỚI NÀY ===
    @Override
    public List<User> findByRoleName(String roleName) {
        return userRepository.findByRoles_Name(roleName);
    }

    /**
     * Thứ tự kiểm quan trọng về HIỆU NĂNG: màn hình /admin/manage-user gọi hàm này cho
     * MỌI dòng, mà phần lớn tài khoản là bác sĩ — để nhánh doctorRepository lên đầu thì
     * ~132 dòng chỉ tốn đúng một truy vấn rồi thoát.
     */
    @Override
    public String whyCannotDelete(User user, String currentUsername) {
        if (user == null) return "Không tìm thấy tài khoản.";
        Long id = user.getId();

        if (currentUsername != null && currentUsername.equals(user.getUsername()))
            return "Không thể xoá tài khoản đang đăng nhập.";

        if (doctorRepository.findByUserId(id).isPresent())
            return "Đây là hồ sơ bác sĩ — lịch hẹn, đánh giá và lịch làm việc đều tham chiếu tới. "
                 + "Hãy xoá hồ sơ bác sĩ trước.";

        long bookings = bookingRepository.countByUserId(id);
        if (bookings > 0)
            return "Tài khoản có " + bookings + " lịch hẹn (kèm hồ sơ bệnh án). Dữ liệu khám bệnh "
                 + "không được xoá — hãy khoá tài khoản thay vì xoá.";

        long tx = walletTransactionRepository.countByUserId(id);
        if (tx > 0)
            return "Tài khoản có " + tx + " giao dịch ví. Sổ tiền phải giữ nguyên để đối soát.";

        long posts = postRepository.countByAuthorId(id);
        if (posts > 0)
            return "Tài khoản là tác giả của " + posts + " bài viết. Hãy chuyển quyền tác giả trước.";

        long decided = leaveRequestRepository.countByApproverId(id)
                     + staffShiftRepository.countByApproverId(id);
        if (decided > 0)
            return "Tài khoản đã ký duyệt " + decided + " đơn nghỉ phép / ca trực. Xoá đi thì các "
                 + "quyết định đó mất người chịu trách nhiệm.";

        return null;
    }

    /**
     * Các bảng dưới đây đều trỏ vào users bằng khoá ngoại KHÔNG cascade, nên phải xoá tay
     * theo đúng thứ tự — thiếu một bảng là MySQL từ chối và admin nhận một câu báo lỗi
     * chung chung không chỉ ra được bảng nào.
     */
    @Override
    @Transactional
    public void deleteAccount(Long id) {
        // Lời mời đổi ca: xoá cả hai chiều, rồi xoá theo từng ca trực của chính người này
        // (một dòng người KHÁC gửi tới ca của họ vẫn chặn được việc xoá staff_shifts).
        shiftCoverRequestRepository.deleteByRequesterId(id);
        shiftCoverRequestRepository.deleteByTargetUserId(id);
        for (StaffShift shift : staffShiftRepository.findByUserId(id)) {
            shiftCoverRequestRepository.deleteByShiftId(shift.getId());
        }
        staffShiftRepository.deleteByUserId(id);

        leaveRequestRepository.deleteByUserId(id);
        allergyRepository.deleteByUserId(id);
        aiChatSessionRepository.deleteByUserId(id);
        notificationRepository.deleteByRecipientId(id);
        staffProfileRepository.deleteByUserId(id);

        // user_roles do JPA tự dọn vì User sở hữu quan hệ @ManyToMany.
        userRepository.deleteById(id);
    }
}