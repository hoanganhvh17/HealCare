package com.bookinghealthy.service;

import com.bookinghealthy.model.Department;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.StaffProfile;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.DoctorRepository;
import com.bookinghealthy.repository.StaffProfileRepository;
import com.bookinghealthy.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Giải mã người đang đăng nhập từ Authentication.
 *
 * Principal có thể là {@link UserDetails} (đăng nhập form) hoặc {@link OAuth2User}
 * (Google/Facebook), nên phải tra username trước rồi mới fallback sang email —
 * đúng cách mà BookingController đã làm. Gom vào một chỗ để các màn hình lịch làm việc
 * không lặp lại khối này.
 */
@Service
public class CurrentUserService {

    @Autowired private UserRepository userRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private StaffProfileRepository staffProfileRepository;

    public User require(Authentication authentication) {
        return find(authentication)
                .orElseThrow(() -> new IllegalStateException("Không xác định được người dùng đang đăng nhập."));
    }

    public Optional<User> find(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        String identifier;
        if (principal instanceof OAuth2User oauthUser) {
            identifier = oauthUser.getAttribute("email");
        } else if (principal instanceof UserDetails userDetails) {
            identifier = userDetails.getUsername();
        } else {
            identifier = principal.toString();
        }

        if (identifier == null) {
            return Optional.empty();
        }

        final String lookup = identifier;
        return userRepository.findByUsername(lookup)
                .or(() -> userRepository.findByEmail(lookup));
    }

    /** Hồ sơ bác sĩ, rỗng nếu người này là lễ tân hoặc admin. */
    public Optional<Doctor> findDoctor(User user) {
        return (user != null) ? doctorRepository.findByUserId(user.getId()) : Optional.empty();
    }

    /** Khoa của người dùng — lễ tân không thuộc khoa nào nên trả null. */
    public Department resolveDepartment(User user) {
        return findDoctor(user).map(Doctor::getDepartment).orElse(null);
    }

    /** Khoa mà người này làm trưởng khoa. Null nghĩa là không phải trưởng khoa. */
    public Department resolveHeadDepartment(User user) {
        if (user == null) {
            return null;
        }
        return staffProfileRepository.findByUserId(user.getId())
                .map(StaffProfile::getHeadOfDepartment)
                .orElse(null);
    }
}
