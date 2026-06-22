package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.Department;
import com.bookinghealthy.model.Doctor;
import com.bookinghealthy.model.Role;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.RoleRepository;
import com.bookinghealthy.service.DepartmentService;
import com.bookinghealthy.service.DoctorService;
//import com.bookinghealthy.service.ImageService;
import com.bookinghealthy.service.UserService;
import jakarta.validation.Path;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping("/admin/manage-doctor")
public class AdminDoctorController {

    @Autowired private DoctorService doctorService;
    @Autowired private UserService userService;
    @Autowired private DepartmentService departmentService;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    // === TIÊM IMAGE SERVICE ===
//    @Autowired private ImageService imageService;
    // 1. HIỂN THỊ DANH SÁCH (READ)
    @GetMapping
    public String listDoctors(Model model) {
        // Chỉ tìm các user là Bác sĩ
        List<User> doctorUsers = userService.findByRoleName("ROLE_DOCTOR");
        // (Lưu ý: Cách này sẽ chậm nếu có hàng ngàn bác sĩ,
        // nhưng với vài trăm bác sĩ thì vẫn chạy tốt)
        model.addAttribute("listDoctors", doctorService.findAll().stream()
                .filter(doc -> doctorUsers.contains(doc.getUser()))
                .toList());
        return "admin/doctor-list";
    }

    // 2. HIỂN THỊ FORM THÊM MỚI (CREATE)
    @GetMapping("/add")
    public String showAddDoctorForm(Model model) {
        // Gửi 1 User và 1 Doctor rỗng
        model.addAttribute("user", new User());
        model.addAttribute("doctor", new Doctor());

        // Gửi danh sách Khoa
        model.addAttribute("allDepartments", departmentService.findAll());
        model.addAttribute("pageTitle", "Thêm mới Bác sĩ");
        return "admin/doctor-form-add"; // -> Tới file HTML THÊM MỚI
    }

    // 3. XỬ LÝ LƯU BÁC SĨ MỚI (SAVE)
    @PostMapping("/save")
    public String saveDoctor(@Valid @ModelAttribute("user") User user,
                             BindingResult userBindingResult,
                             @ModelAttribute("doctor") Doctor doctor,
                             @RequestParam(name = "password", required = false) String rawPassword,
                             @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile,
                             Model model,
                             RedirectAttributes ra) {

        // Bắt lỗi validation cho User (tên, email...)
        if (userBindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Thêm mới Bác sĩ");
            model.addAttribute("allDepartments", departmentService.findAll());
            return "admin/doctor-form-add";
        }

        // Bắt lỗi validation Mật khẩu
        if (rawPassword == null || rawPassword.isEmpty()) {
            userBindingResult.rejectValue("password", "NotBlank", "Mật khẩu là bắt buộc khi tạo mới");
            model.addAttribute("pageTitle", "Thêm mới Bác sĩ");
            model.addAttribute("allDepartments", departmentService.findAll());
            return "admin/doctor-form-add";
        }

        try {
            // 1. Tạo User trước
            Role doctorRole = roleRepository.findByName("ROLE_DOCTOR").orElseThrow();
            user.setRoles(Set.of(doctorRole));
            user.setPassword(passwordEncoder.encode(rawPassword));
            User savedUser = userService.save(user);

            // --- BẮT ĐẦU ĐOẠN THÊM VÀO: XỬ LÝ LƯU ẢNH ---
            try {
                if (avatarFile != null && !avatarFile.isEmpty()) {
                    // 1. Tạo tên file duy nhất theo thời gian hiện tại
                    String fileName = System.currentTimeMillis() + "_" + avatarFile.getOriginalFilename();

                    // 2. Chỉ định thư mục lưu ngang hàng với file .jar hoặc pom.xml (CHẠY ĐƯỢC CẢ LOCAL VÀ VPS)
                    String uploadDir = "uploads/";
                    File dir = new File(uploadDir);
                    if (!dir.exists()) {
                        dir.mkdirs(); // Tự động tạo thư mục nếu chưa có
                    }

                    // 3. Đẩy file vào thư mục
                    java.nio.file.Path path = Paths.get(uploadDir + fileName);
                    Files.write(path, avatarFile.getBytes());

                    // 4. Gắn tên ảnh vào đối tượng User để lát nữa lưu xuống DB
                    user.setAvatar(fileName);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            // --- KẾT THÚC ĐOẠN THÊM VÀO ---

            // 2. Liên kết User với Doctor và Lưu
            doctor.setUser(savedUser);
            doctorService.save(doctor);

            ra.addFlashAttribute("successMessage", "Đã tạo Bác sĩ mới thành công.");
            return "redirect:/admin/manage-doctor";

        } catch (DataIntegrityViolationException e) {
            // 3. Bắt lỗi trùng lặp (username/email)
            userBindingResult.rejectValue("username", "Duplicate", "Username hoặc Email đã tồn tại.");
            model.addAttribute("pageTitle", "Thêm mới Bác sĩ");
            model.addAttribute("allDepartments", departmentService.findAll());
            return "admin/doctor-form-add";
        }
    }

    // 4. HIỂN THỊ FORM SỬA (UPDATE)
    @GetMapping("/edit/{id}")
    public String showEditDoctorForm(@PathVariable("id") Long id, Model model, RedirectAttributes ra) {
        Optional<Doctor> doctorOpt = doctorService.findById(id);
        if (doctorOpt.isPresent()) {
            Doctor doctor = doctorOpt.get();
            model.addAttribute("doctor", doctor);
            model.addAttribute("user", doctor.getUser()); // Gửi user liên quan
            model.addAttribute("allDepartments", departmentService.findAll());
            model.addAttribute("pageTitle", "Chỉnh sửa Bác sĩ");
            return "admin/doctor-form-edit"; // -> Tới file HTML SỬA
        } else {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy Bác sĩ ID: " + id);
            return "redirect:/admin/manage-doctor";
        }
    }

    // 5. XỬ LÝ LƯU (UPDATE)
    @PostMapping("/update")
    public String updateDoctor(@Valid @ModelAttribute("user") User user,
                               BindingResult userBindingResult,
                               @ModelAttribute("doctor") Doctor doctor,
                               @RequestParam(name = "password", required = false) String rawPassword,
                               @RequestParam(value = "avatarFile", required = false) MultipartFile avatarFile,
                               Model model,
                               RedirectAttributes ra) {

        // Chỉ validate User (vì doctor chỉ có ID)
        if (userBindingResult.hasErrors()) {
            model.addAttribute("pageTitle", "Chỉnh sửa Bác sĩ");
            model.addAttribute("allDepartments", departmentService.findAll());
            return "admin/doctor-form-edit";
        }

        try {
            // 1. Lấy Doctor và User gốc từ DB
            Doctor existingDoctor = doctorService.findById(doctor.getId()).orElseThrow();
            User existingUser = existingDoctor.getUser();

            // 2. Cập nhật User
            existingUser.setFullName(user.getFullName());
            existingUser.setEmail(user.getEmail());
            existingUser.setUsername(user.getUsername());
            existingUser.setPhone(user.getPhone());
            if (rawPassword != null && !rawPassword.isEmpty()) {
                existingUser.setPassword(passwordEncoder.encode(rawPassword));
            }

            // --- XỬ LÝ LƯU ẢNH KHI UPDATE ---
            try {
                if (avatarFile != null && !avatarFile.isEmpty()) {
                    String fileName = System.currentTimeMillis() + "_" + avatarFile.getOriginalFilename();
                    String uploadDir = "uploads/";
                    File dir = new File(uploadDir);
                    if (!dir.exists()) dir.mkdirs();

                    java.nio.file.Path path = Paths.get(uploadDir + fileName);
                    Files.write(path, avatarFile.getBytes());

                    // Gắn tên ảnh mới vào user
                    existingUser.setAvatar(fileName);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            // --- KẾT THÚC ---
            userService.save(existingUser); // Lưu User

            // 3. Cập nhật Doctor
            existingDoctor.setDepartment(doctor.getDepartment());
            existingDoctor.setDegree(doctor.getDegree());
            existingDoctor.setPrice(doctor.getPrice());
            existingDoctor.setExperienceYears(doctor.getExperienceYears());
            existingDoctor.setBio(doctor.getBio());
            doctorService.save(existingDoctor); // Lưu Doctor

            ra.addFlashAttribute("successMessage", "Đã cập nhật Bác sĩ thành công.");
            return "redirect:/admin/manage-doctor";

        } catch (DataIntegrityViolationException e) {
            userBindingResult.rejectValue("username", "Duplicate", "Username hoặc Email đã tồn tại.");
            model.addAttribute("pageTitle", "Chỉnh sửa Bác sĩ");
            model.addAttribute("allDepartments", departmentService.findAll());
            return "admin/doctor-form-edit";
        }
    }

    // 6. XỬ LÝ XÓA (DELETE)
    @GetMapping("/delete/{id}")
    public String deleteDoctor(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            // (Chúng ta có thể cần xóa User liên quan,
            // nhưng hiện tại chỉ xóa Doctor)
            doctorService.deleteById(id);
            ra.addFlashAttribute("successMessage", "Đã xóa Bác sĩ thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", "Không thể xóa Bác sĩ (có thể do đã có Lịch hẹn).");
        }
        return "redirect:/admin/manage-doctor";
    }
}