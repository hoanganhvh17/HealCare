package com.bookinghealthy.config;

import com.bookinghealthy.config.DoctorSeedData.SeedDoctor;
import com.bookinghealthy.model.*;
import com.bookinghealthy.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DoctorRepository doctorRepository;
    @Autowired private ScheduleRepository scheduleRepository;
    @Autowired private StaffProfileRepository staffProfileRepository;

    @org.springframework.beans.factory.annotation.Value("${seed.enabled}")
    private boolean seedEnabled;

    @org.springframework.beans.factory.annotation.Value("${seed.admin.username}")
    private String seedAdminUsername;

    @org.springframework.beans.factory.annotation.Value("${seed.admin.password}")
    private String seedAdminPassword;

    @org.springframework.beans.factory.annotation.Value("${seed.demo-accounts.enabled}")
    private boolean seedDemoAccounts;

    @org.springframework.beans.factory.annotation.Value("${seed.doctor.default-password}")
    private String seedDoctorPassword;

    @Override
    public void run(String... args) throws Exception {

        if (!seedEnabled) {
            System.out.println(">>> Bỏ qua khởi tạo dữ liệu (seed.enabled=false).");
            return;
        }
        if (userRepository.count() == 0) {
            System.out.println(">>> ĐANG KHỞI TẠO DỮ LIỆU BẰNG JAVA...");
            Role adminRole = new Role(); adminRole.setName("ROLE_ADMIN");
            Role doctorRole = new Role(); doctorRole.setName("ROLE_DOCTOR");
            Role userRole = new Role(); userRole.setName("ROLE_USER");
            Role receptionistRole = new Role(); receptionistRole.setName("ROLE_RECEPTIONIST");
            roleRepository.saveAll(List.of(adminRole, doctorRole, userRole, receptionistRole));
            String pass123 = passwordEncoder.encode(seedDoctorPassword);
            String passABC = passwordEncoder.encode("abc123!@#");
            String adminPass = passwordEncoder.encode(seedAdminPassword);
            if ("admin123".equals(seedAdminPassword)) {
                System.err.println("!!! CẢNH BÁO: tài khoản admin đang dùng mật khẩu mặc định. "
                        + "Đặt biến môi trường SEED_ADMIN_PASSWORD trước khi chạy thật.");
            }
            User admin = new User(null, seedAdminUsername, "admin@gmail.com", adminPass, "Administrator", "0900000000", null, "Nam", AuthProvider.LOCAL, Set.of(adminRole), BigDecimal.ZERO);
            User patientTom = new User(null, "patient_tom", "tom@gmail.com", pass123, "Tom Patient", "0900000001", null, "Nam", AuthProvider.LOCAL, Set.of(userRole), BigDecimal.ZERO);
            User testSang = new User(null, "testsang31", "testsang31@gmail.com", passABC, "Test Sang 31", "0900000002", null, "Nam", AuthProvider.LOCAL, Set.of(userRole), BigDecimal.ZERO);
            List<User> doctorUsers = List.of(
                    new User(null, "doctor_walter", "walter@gmail.com", pass123, "Walter White", "0912345678", "doctor-1.jpg", "Nam", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "doctor_sarah", "sarah@gmail.com", pass123, "Sarah Connor", "0987654321", "doctor-2.jpg", "Nữ", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_thuha", "thuha.nguyen@example.com", pass123, "Nguyễn Thị Thu Hà", "0905123456", "doctor-3.jpg", "Nữ", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_quangdung", "quangdung.tran@example.com", pass123, "Trần Quang Dũng", "0978123456", "doctor-4.jpg", "Nam", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_minhtuan", "minhtuan.le@example.com", pass123, "Lê Minh Tuấn", "0912349876", "doctor-5.jpg", "Nam", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_hongnhung", "hongnhung.pham@example.com", pass123, "Phạm Hồng Nhung", "0932123456", "doctor-6.jpg", "Nữ", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_lananh", "lananh.do@example.com", pass123, "Đỗ Thị Lan Anh", "0988123123", "doctor-7.jpg", "Nữ", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_vanquan", "vanquan.nguyen@example.com", pass123, "Nguyễn Văn Quân", "0966668888", "doctor-8.jpg", "Nam", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_congphu", "congphu.truong@example.com", pass123, "Trương Công Phú", "0912445566", "doctor-9.jpg", "Nam", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_luuthimai", "luuthimai@example.com", pass123, "Lưu Thị Mai", "0977554433", "doctor-10.jpg", "Nữ", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_huuduc", "huuduc.phan@example.com", pass123, "Phan Hữu Đức", "0912333444", "doctor-11.jpg", "Nam", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_kimlien", "kimlien.vu@example.com", pass123, "Vũ Thị Kim Liên", "0908222333", "doctor-12.jpg", "Nữ", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_huukhanh", "huukhanh.nguyen@example.com", pass123, "Nguyễn Hữu Khánh", "0977445566", "doctor-13.jpg", "Nam", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_thaodang", "thaodang@example.com", pass123, "Đặng Thanh Thảo", "0912999888", "doctor-14.jpg", "Nữ", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_quoccuong", "quoccuong.bui@example.com", pass123, "Bùi Quốc Cường", "0935667788", "doctor-15.jpg", "Nam", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_thihanh", "thihanh.nguyen@example.com", pass123, "Nguyễn Thị Hạnh", "0988665544", "doctor-16.jpg", "Nữ", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_vanhau", "vanhau.pham@example.com", pass123, "Phạm Văn Hậu", "0909777555", "doctor-17.jpg", "Nam", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_baongoc", "baongoc.tran@example.com", pass123, "Trần Bảo Ngọc", "0932111777", "doctor-18.jpg", "Nữ", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_vanson", "vanson.doan@example.com", pass123, "Đoàn Văn Sơn", "0977000111", "doctor-19.jpg", "Nam", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_hothihuong", "hothihuong@example.com", pass123, "Hồ Thị Hương", "0918000222", "doctor-20.jpg", "Nữ", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_lamanhdung", "lamanhdung@example.com", pass123, "Lâm Anh Dũng", "0933444555", "doctor-21.jpg", "Nam", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO),
                    new User(null, "bs_thanhtam", "thanhtam.nguyen@example.com", pass123, "Nguyễn Thanh Tâm", "0909666777", "doctor-22.jpg", "Nữ", AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO)
            );
            userRepository.save(admin);
            if (seedDemoAccounts) {
                userRepository.save(patientTom);
                userRepository.save(testSang);
            } else {
                System.out.println(">>> Bỏ qua tài khoản demo (seed.demo-accounts.enabled=false).");
            }
            List<User> savedDoctorUsers = userRepository.saveAll(doctorUsers);
            List<Department> departments = List.of(
                    new Department(null, "Tim mạch", "Chuyên thăm khám...", "dept-1.jpg"),
                    new Department(null, "Nội thần kinh", "Chuyên điều trị đột quỵ...", "dept-2.jpg"),
                    new Department(null, "Nhi khoa", "Chăm sóc trẻ nhỏ.", "dept-3.jpg"),
                    new Department(null, "Da liễu", "Điều trị các bệnh về da.", "dept-4.jpg"),
                    new Department(null, "Chấn thương chỉnh hình", "Phẫu thuật chỉnh hình.", "dept-5.jpg"),
                    new Department(null, "Nhãn khoa", "Điều trị cận thị.", "dept-6.jpg"),
                    new Department(null, "Sản phụ khoa", "Chăm sóc thai sản.", "dept-7.jpg"),
                    new Department(null, "Tiêu hóa", "Điều trị viêm loét dạ dày.", "dept-8.jpg"),
                    new Department(null, "Tiết niệu", "Điều trị sỏi thận.", "dept-1.jpg"),
                    new Department(null, "Nội tiết", "Điều trị bệnh tiểu đường.", "dept-2.jpg"),
                    new Department(null, "Ung bướu", "Điều trị các bệnh ung thư.", "dept-3.jpg"),
                    new Department(null, "Tâm thần", "Tư vấn và điều trị rối loạn lo âu.", "dept-4.jpg"),
                    new Department(null, "Tai Mũi Họng", "Điều trị viêm xoang.", "dept-5.jpg"),
                    new Department(null, "Răng hàm mặt", "Nha khoa thẩm mỹ.", "dept-6.jpg"),
                    new Department(null, "Hô hấp", "Điều trị bệnh hen suyễn.", "dept-7.jpg"),
                    new Department(null, "Cơ xương khớp", "Điều trị viêm khớp.", "dept-8.jpg"),
                    new Department(null, "Thận học", "Điều trị suy thận.", "dept-1.jpg"),
                    new Department(null, "Huyết học", "Điều trị rối loạn đông máu.", "dept-2.jpg"),
                    new Department(null, "Chẩn đoán hình ảnh", "Phân tích phim X-quang.", "dept-3.jpg"),
                    new Department(null, "Gây mê hồi sức", "Gây mê phẫu thuật.", "dept-4.jpg"),
                    new Department(null, "Cấp cứu", "Xử lý các ca chấn thương.", "dept-5.jpg"),
                    new Department(null, "Y học gia đình", "Chăm sóc sức khỏe tổng quát.", "dept-6.jpg")
            );
            departmentRepository.saveAll(departments);

            Map<String, Department> departmentsMap = departmentRepository.findAll().stream()
                    .collect(Collectors.toMap(Department::getName, dept -> dept));

            List<String> bios = List.of(
                    "Tiến sĩ, Bác sĩ chuyên khoa Tim mạch...", "Thạc sĩ, Bác sĩ chuyên khoa Nội thần kinh...", "Bác sĩ Nguyễn Thị Thu Hà – chuyên khoa Nhi...",
                    "Bác sĩ Trần Quang Dũng – chuyên khoa Da liễu...", "Bác sĩ Lê Minh Tuấn – hơn 15 năm kinh nghiệm...", "Bác sĩ Phạm Hồng Nhung – chuyên khoa Mắt...",
                    "Bác sĩ Đỗ Thị Lan Anh – hơn 14 năm kinh nghiệm...", "Bác sĩ Nguyễn Văn Quân – chuyên khoa Tiêu hóa...", "Bác sĩ Trương Công Phú – chuyên khoa Tiết niệu...",
                    "Bác sĩ Lưu Thị Mai – hơn 16 năm công tác...", "Bác sĩ Phan Hữu Đức – chuyên khoa Ung bướu...", "Bác sĩ Vũ Thị Kim Liên – hơn 11 năm kinh nghiệm...",
                    "Bác sĩ Nguyễn Hữu Khánh – chuyên khoa Tai Mũi Họng...", "Bác sĩ Đặng Thanh Thảo – hơn 7 năm kinh nghiệm...", "Bác sĩ Bùi Quốc Cường – chuyên khoa Hô hấp...",
                    "Bác sĩ Nguyễn Thị Hạnh – hơn 10 năm kinh nghiệm...", "Bác sĩ Phạm Văn Hậu – chuyên khoa Thận học...", "Bác sĩ Trần Bảo Ngọc – chuyên khoa Huyết học...",
                    "Bác sĩ Đoàn Văn Sơn – hơn 12 năm kinh nghiệm...", "Bác sĩ Hồ Thị Hương – hơn 13 năm kinh nghiệm...", "Bác sĩ Lâm Anh Dũng – chuyên khoa Cấp cứu...",
                    "Bác sĩ Nguyễn Thanh Tâm – hơn 11 năm kinh nghiệm..."
            );

            List<Doctor> doctorsToSave = new ArrayList<>();
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(0), departmentsMap.get("Tim mạch"), bios.get(0), 15, "Tiến sĩ", new BigDecimal("500000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(1), departmentsMap.get("Nội thần kinh"), bios.get(1), 10, "Thạc sĩ", new BigDecimal("400000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(2), departmentsMap.get("Nhi khoa"), bios.get(2), 12, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(3), departmentsMap.get("Da liễu"), bios.get(3), 9, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(4), departmentsMap.get("Chấn thương chỉnh hình"), bios.get(4), 15, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(5), departmentsMap.get("Nhãn khoa"), bios.get(5), 10, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(6), departmentsMap.get("Sản phụ khoa"), bios.get(6), 14, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(7), departmentsMap.get("Tiêu hóa"), bios.get(7), 13, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(8), departmentsMap.get("Tiết niệu"), bios.get(8), 8, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(9), departmentsMap.get("Nội tiết"), bios.get(9), 16, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(10), departmentsMap.get("Ung bướu"), bios.get(10), 18, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(11), departmentsMap.get("Tâm thần"), bios.get(11), 11, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(12), departmentsMap.get("Tai Mũi Họng"), bios.get(12), 9, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(13), departmentsMap.get("Răng hàm mặt"), bios.get(13), 7, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(14), departmentsMap.get("Hô hấp"), bios.get(14), 13, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(15), departmentsMap.get("Cơ xương khớp"), bios.get(15), 10, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(16), departmentsMap.get("Thận học"), bios.get(16), 9, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(17), departmentsMap.get("Huyết học"), bios.get(17), 15, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(18), departmentsMap.get("Chẩn đoán hình ảnh"), bios.get(18), 12, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(19), departmentsMap.get("Gây mê hồi sức"), bios.get(19), 13, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(20), departmentsMap.get("Cấp cứu"), bios.get(20), 10, "Bác sĩ", new BigDecimal("300000")));
            doctorsToSave.add(new Doctor(null, savedDoctorUsers.get(21), departmentsMap.get("Y học gia đình"), bios.get(21), 11, "Bác sĩ", new BigDecimal("300000")));

            List<Doctor> savedDoctors = doctorRepository.saveAll(doctorsToSave);
            Schedule s1 = new Schedule(null, savedDoctors.get(0), DayOfWeek.MONDAY, LocalTime.parse("08:00:00"), LocalTime.parse("11:00:00"), null);
            Schedule s2 = new Schedule(null, savedDoctors.get(0), DayOfWeek.TUESDAY, LocalTime.parse("08:00:00"), LocalTime.parse("11:00:00"), null);
            Schedule s3 = new Schedule(null, savedDoctors.get(0), DayOfWeek.THURSDAY, LocalTime.parse("14:00:00"), LocalTime.parse("16:00:00"), null);
            Schedule s4 = new Schedule(null, savedDoctors.get(2), DayOfWeek.TUESDAY, LocalTime.parse("08:00:00"), LocalTime.parse("12:00:00"), null);
            Schedule s5 = new Schedule(null, savedDoctors.get(2), DayOfWeek.TUESDAY, LocalTime.parse("13:30:00"), LocalTime.parse("16:30:00"), null);

            scheduleRepository.saveAll(List.of(s1, s2, s3, s4, s5));

            System.out.println(">>> KHỞI TẠO DỮ LIỆU JAVA HOÀN TẤT (ĐÃ THÊM BIGDECIMAL) <<<");
        }
        ensureReceptionistAccount();
        ensureExtraDoctors();
        ensureStaffProfiles();
        ensureHeadDoctors();
    }

    private void ensureStaffProfiles() {
        int created = 0;
        for (Doctor doctor : doctorRepository.findAll()) {
            User user = doctor.getUser();
            if (user == null || staffProfileRepository.existsByUserId(user.getId())) {
                continue;
            }
            int experience = (doctor.getExperienceYears() != null) ? doctor.getExperienceYears() : 0;
            boolean heavy = doctor.getDepartment() != null
                    && LeavePolicy.HEAVY_DEPARTMENTS.contains(doctor.getDepartment().getName());

            StaffProfile profile = new StaffProfile();
            profile.setUser(user);
            profile.setHireDate(LocalDate.now().minusYears(experience));
            profile.setWorkCondition(heavy ? WorkCondition.HEAVY : WorkCondition.NORMAL);
            profile.setSocialInsuranceYears(experience);
            profile.setCarriedOverDays(0);
            staffProfileRepository.save(profile);
            created++;
        }

        for (User staff : userRepository.findAll()) {
            boolean isReceptionist = staff.getRoles() != null && staff.getRoles().stream()
                    .anyMatch(role -> "ROLE_RECEPTIONIST".equals(role.getName()));

            if (!isReceptionist || staffProfileRepository.existsByUserId(staff.getId())) {
                continue;
            }

            StaffProfile profile = new StaffProfile();
            profile.setUser(staff);
            profile.setHireDate(LocalDate.now());
            profile.setWorkCondition(WorkCondition.NORMAL);
            profile.setSocialInsuranceYears(0);
            profile.setCarriedOverDays(0);
            staffProfileRepository.save(profile);
            created++;
        }

        if (created > 0) {
            System.out.println(">>> Đã tạo " + created + " hồ sơ nhân sự (ngày vào làm + điều kiện lao động).");
        }
    }

    private void ensureHeadDoctors() {
        Role headRole = roleRepository.findByName("ROLE_HEAD_DOCTOR")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName("ROLE_HEAD_DOCTOR");
                    System.out.println(">>> Tạo mới vai trò ROLE_HEAD_DOCTOR");
                    return roleRepository.save(role);
                });

        int assigned = 0;
        for (Department department : departmentRepository.findAll()) {
            if (!staffProfileRepository.findByHeadOfDepartmentId(department.getId()).isEmpty()) {
                continue;
            }

            Doctor head = doctorRepository.findByDepartmentId(department.getId()).stream()
                    .max(java.util.Comparator.comparing(
                            doctor -> doctor.getExperienceYears() != null ? doctor.getExperienceYears() : 0))
                    .orElse(null);

            if (head == null || head.getUser() == null) {
                continue;
            }

            User headUser = head.getUser();
            Set<Role> roles = new HashSet<>(headUser.getRoles() != null ? headUser.getRoles() : Set.of());
            roles.add(headRole);
            headUser.setRoles(roles);
            userRepository.save(headUser);

            StaffProfile profile = staffProfileRepository.findByUserId(headUser.getId())
                    .orElseGet(() -> {
                        StaffProfile fresh = new StaffProfile();
                        fresh.setUser(headUser);
                        fresh.setHireDate(LocalDate.now());
                        return fresh;
                    });
            profile.setHeadOfDepartment(department);
            staffProfileRepository.save(profile);
            assigned++;
        }

        if (assigned > 0) {
            System.out.println(">>> Đã gán trưởng khoa cho " + assigned
                    + " chuyên khoa (đăng nhập bằng chính tài khoản bác sĩ đó, mật khẩu 123456).");
        }
    }

    private void ensureReceptionistAccount() {
        Role receptionistRole = roleRepository.findByName("ROLE_RECEPTIONIST")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName("ROLE_RECEPTIONIST");
                    System.out.println(">>> Tạo mới vai trò ROLE_RECEPTIONIST");
                    return roleRepository.save(role);
                });

        if (!userRepository.existsByUsername("receptionist")) {
            // Thứ tự tham số theo @AllArgsConstructor của User:
            // (id, username, email, password, fullName, phone, avatar, gender, authProvider, roles, balance)
            User receptionist = new User(null, "receptionist", "receptionist@gmail.com",
                    passwordEncoder.encode(seedDoctorPassword), "Lễ tân NNL Hospital", "0900000009", null,
                    "Nữ", AuthProvider.LOCAL, Set.of(receptionistRole), BigDecimal.ZERO);
            userRepository.save(receptionist);
            System.out.println(">>> Tạo mới tài khoản lễ tân: receptionist (mật khẩu theo seed.doctor.default-password)");
        }
    }

    private static final String[] PHONE_PREFIXES = {
            "090", "091", "094", "096", "097", "098", "032", "033", "034", "035", "036",
            "037", "038", "039", "070", "076", "077", "078", "079", "081", "082", "083",
            "085", "086", "088"
    };

    private static final String[] BIO_CLOSINGS = {
            "Bác sĩ nhận khám theo lịch hẹn tại NNL Hospital, tư vấn rõ phác đồ và chi phí trước khi điều trị.",
            "Bác sĩ ưu tiên điều trị bảo tồn, chỉ định cận lâm sàng hợp lý và theo dõi sát sau điều trị.",
            "Bác sĩ khám và tư vấn bằng tiếng Việt, tiếng Anh; hỗ trợ tái khám trực tuyến khi người bệnh ở xa.",
            "Bác sĩ có kinh nghiệm xử trí ca khó và phối hợp hội chẩn đa chuyên khoa tại NNL Hospital.",
            "Bác sĩ dành thời gian giải thích rõ tình trạng bệnh và hướng dẫn chăm sóc tại nhà cho người bệnh."
    };

    private static final DayOfWeek[][] SCHEDULE_DAYS = {
            {DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY},
            {DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY},
            {DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY},
            {DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY},
            {DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}
    };

    private void ensureExtraDoctors() {
        if (departmentRepository.count() == 0) {
            System.out.println(">>> Bỏ qua seed bác sĩ bổ sung: DB chưa có chuyên khoa nào.");
            return;
        }

        Role doctorRole = roleRepository.findByName("ROLE_DOCTOR")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName("ROLE_DOCTOR");
                    return roleRepository.save(role);
                });

        Map<String, Department> departmentsMap = departmentRepository.findAll().stream()
                .collect(Collectors.toMap(Department::getName, dept -> dept, (a, b) -> a));
        String pass123 = passwordEncoder.encode(seedDoctorPassword);
        int seq = 0;
        int created = 0;
        for (Map.Entry<String, List<SeedDoctor>> entry : DoctorSeedData.BY_DEPARTMENT.entrySet()) {
            Department department = departmentsMap.get(entry.getKey());
            if (department == null) {
                System.out.println(">>> Không tìm thấy chuyên khoa '" + entry.getKey()
                        + "' trong DB, bỏ qua các bác sĩ của khoa này.");
                continue;
            }

            int position = 0;
            for (SeedDoctor seed : entry.getValue()) {
                seq++;
                position++;

                String slug = slugify(seed.fullName());
                String username = "bs_" + slug;
                if (userRepository.existsByUsername(username)) {
                    continue;
                }
                User user = userRepository.save(new User(null, username, slug + "@nnlhospital.vn", pass123,
                        seed.fullName(), generatePhone(seq), "bs-" + slug + ".jpg", seed.gender(),
                        AuthProvider.LOCAL, Set.of(doctorRole), BigDecimal.ZERO));

                Doctor doctor = doctorRepository.save(new Doctor(null, user, department,
                        buildBio(seed, department.getName(), position), seed.experienceYears(),
                        seed.degree(), BigDecimal.valueOf(seed.price())));

                scheduleRepository.saveAll(buildSchedules(doctor, position));
                created++;
            }
        }

        if (created > 0) {
            System.out.println(">>> Đã bổ sung " + created + " bác sĩ mới (mật khẩu: 123456).");
        }
    }

    private String slugify(String fullName) {
        String noMark = Normalizer.normalize(fullName, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        // đ/Đ không tách dấu theo NFD nên phải thay tay.
        return noMark.replace("đ", "d").replace("Đ", "D").toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String generatePhone(int seq) {
        return PHONE_PREFIXES[seq % PHONE_PREFIXES.length] + String.format("%07d", 2450000 + seq * 1373L);
    }

    private String buildBio(SeedDoctor seed, String departmentName, int position) {
        return seed.degree() + " " + seed.fullName() + " có " + seed.experienceYears()
                + " năm kinh nghiệm khám và điều trị tại chuyên khoa " + departmentName + ". "
                + "Thế mạnh chuyên môn: " + seed.expertise() + ". "
                + "Đào tạo: " + seed.training() + ". "
                + BIO_CLOSINGS[(position - 1) % BIO_CLOSINGS.length];
    }

    private List<Schedule> buildSchedules(Doctor doctor, int position) {
        DayOfWeek[] days = SCHEDULE_DAYS[(position - 1) % SCHEDULE_DAYS.length];
        LocalTime start = (position % 2 == 1) ? LocalTime.of(7, 30) : LocalTime.of(13, 30);
        LocalTime end = (position % 2 == 1) ? LocalTime.of(11, 30) : LocalTime.of(17, 30);

        List<Schedule> schedules = new ArrayList<>();
        for (DayOfWeek day : days) {
            schedules.add(new Schedule(null, doctor, day, start, end, null));
        }
        return schedules;
    }
}