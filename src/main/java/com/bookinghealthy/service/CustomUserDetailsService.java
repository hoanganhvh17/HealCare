//package com.bookinghealthy.service;
//
//import com.bookinghealthy.model.User;
//import com.bookinghealthy.repository.UserRepository;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.security.core.GrantedAuthority;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.core.userdetails.UserDetails;
//import org.springframework.security.core.userdetails.UserDetailsService;
//import org.springframework.security.core.userdetails.UsernameNotFoundException;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional; // Thêm import này
//
//import java.util.Set;
//import java.util.stream.Collectors;
//
//@Service
//public class CustomUserDetailsService implements UserDetailsService {
//
//    @Autowired
//    private UserRepository userRepository;
//
//    /**
//     * Ghi đè phương thức loadUserByUsername.
//     * Thêm @Transactional để đảm bảo session Hibernate tồn tại đủ lâu
//     * để tải các vai trò (roles) một cách an toàn.
//     */
//    @Override
//    @Transactional(readOnly = true) // Đảm bảo phiên làm việc tồn tại
//    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
//
//        // 1. Tìm user bằng username HOẶC email (Sử dụng @Query JOIN FETCH đã sửa từ trước)
//        User user = userRepository.findByUsername(usernameOrEmail)
//                .orElseGet(() -> userRepository.findByEmail(usernameOrEmail)
//                        .orElseThrow(() ->
//                                new UsernameNotFoundException("User not found with username or email: " + usernameOrEmail)
//                        ));
//
//        // 2. Lấy Roles (Vì có @Transactional, việc này 100% an toàn)
//        Set<GrantedAuthority> authorities = user.getRoles().stream()
//                .map(role -> new SimpleGrantedAuthority(role.getName()))
//                .collect(Collectors.toSet());
//
//        // 3. Trả về đối tượng UserDetails
//        return new org.springframework.security.core.userdetails.User(
//                user.getUsername(), // Luôn dùng username làm định danh chính
//                user.getPassword(),
//                authorities
//        );
//    }
//}  (Tối 13/11)

package com.bookinghealthy.service;

import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.security.CustomUserDetails; // <-- QUAN TRỌNG: Import class này
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional(readOnly = true) // Giữ phiên làm việc để load Roles (Lazy) an toàn
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {

        // 1. Tìm user trong DB
        User user = userRepository.findByUsername(usernameOrEmail)
                .orElseGet(() -> userRepository.findByEmail(usernameOrEmail)
                        .orElseThrow(() ->
                                new UsernameNotFoundException("Không tìm thấy người dùng: " + usernameOrEmail)
                        ));

        // 2. Trả về CustomUserDetails (Chứa Avatar, FullName...)
        // Class này sẽ tự động xử lý việc map Roles sang Authorities bên trong nó.
        return new CustomUserDetails(user);
    }
}