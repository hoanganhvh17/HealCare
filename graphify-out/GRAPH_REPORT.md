# Graph Report - .  (2026-07-26)

## Corpus Check
- 310 files · ~160,952 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2361 nodes · 5782 edges · 121 communities (114 shown, 7 thin omitted)
- Extraction: 88% EXTRACTED · 12% INFERRED · 0% AMBIGUOUS · INFERRED: 687 edges (avg confidence: 0.81)
- Token cost: 834,572 input · 0 output

## Community Hubs (Navigation)
- Quản lý bài viết (admin)
- Lịch làm việc & chặn giờ
- Quản lý người dùng (admin)
- Danh sách bác sĩ công khai
- VNPay & thanh toán đặt lịch
- Hồ sơ & lịch hẹn bệnh nhân
- Danh mục dịch vụ (admin)
- Màn hình hàng đợi khám
- Quầy lễ tân
- Endpoint AI cho bác sĩ
- Script chat frontend
- Repository & tra cứu User
- Giao diện lịch làm việc
- API khung giờ & thanh toán
- Form bác sĩ & tuyển dụng (admin)
- Hồ sơ bệnh án & chat bác sĩ
- Entity StaffShift
- Hành động dashboard bác sĩ
- API thông báo nhân sự
- Yêu cầu thay ca
- Dịch vụ xuất PDF
- Endpoint gợi ý khung giờ AI
- Entity & repository Doctor
- Lưu phiên chat AI
- Service lịch làm việc nhân sự
- Hợp đồng LeaveService
- Entity MedicalRecord
- Chế độ gọi rảnh tay
- Lõi BookingService
- Quản lý chuyên khoa
- Phê duyệt của trưởng khoa
- Entity LeaveRequest
- API trợ lý bác sĩ
- Hồ sơ bệnh án bác sĩ soạn
- Entity JobPosting
- Module giọng nói dùng chung
- Tính quota nghỉ phép
- Hồ sơ & đơn thuốc bệnh nhân
- Luật trung thực của trợ lý AI
- Role & phân lớp kiến trúc
- LeavePolicy & loại ca
- Controller bác sĩ (admin)
- API & DTO bác sĩ
- Lớp cha lịch làm việc
- Lưới khung giờ & ca khám
- Thông tin user OAuth2
- Controller tuyển dụng (admin)
- Bệnh nhân sửa lịch hẹn
- Khởi tạo dữ liệu seed
- Seed role & tài khoản
- LeaveServiceImpl
- Vai trò trực & kết quả
- Entity Department
- Entity StaffProfile
- Controller AI cho admin
- ReceptionServiceImpl
- Dựng sự kiện lịch
- Bộ nhớ AI & luật giọng nói
- Xử lý đăng nhập OAuth2
- Loại nghỉ phép
- Trang chủ công khai
- Quy ước giọng nói & ngôn ngữ
- Dịch vụ gửi email
- In phiếu thu & đơn thuốc
- Tuyển dụng & ứng tuyển
- Entity Candidate
- Luật seed & uploads
- Entity AiRule
- Xét ứng viên (admin)
- Đặt lịch tại quầy
- Luật thanh toán & cấu hình
- Luật đồng thời đặt lịch
- Lý do nghỉ việc riêng
- Controller đăng nhập & đăng ký
- Thống kê dashboard admin
- Nghỉ nửa ngày
- Entity Allergy
- MedicalAddendum
- MedicalAttachment
- Entity VitalSign
- Hàm phân tích câu của AI
- Controller đặt lịch (admin)
- Controller chat AI & khóa slot
- API thống kê dashboard
- DTO request/response AI
- Quy ước tài liệu & template
- SecurityConfig
- Quản lý lịch hẹn bác sĩ
- Danh sách lịch hẹn lễ tân
- Chi tiết bác sĩ công khai
- Giới hạn hủy & dời lịch
- Hồ sơ bệnh án (admin)
- Yêu cầu đặt lịch tới bác sĩ
- Route lịch làm việc bác sĩ
- Thông tin OAuth2 Facebook
- CustomOAuth2UserService
- Điểm khởi động ứng dụng
- Thông tin OAuth2 Google
- Cron đăng ký ca khám
- Cấu hình tài nguyên MVC
- Dashboard lễ tân
- Route lịch làm việc lễ tân
- Cấu hình PasswordEncoder
- Dọn booking hết hạn
- Bẫy build & kiểm thử
- Enum CandidateStatus
- Cấu hình lịch chạy
- Script chat admin
- UserController rỗng
- DTO đổi mật khẩu
- Script chat bác sĩ cũ
- Trang lỗi
- Trang cơ hội nghề nghiệp
- Trang giới thiệu & liên hệ
- Gốc project Maven

## God Nodes (most connected - your core abstractions)
1. `User` - 154 edges
2. `BookingRepository` - 88 edges
3. `Booking` - 83 edges
4. `Service` - 78 edges
5. `Doctor` - 72 edges
6. `StaffScheduleServiceImpl` - 60 edges
7. `StaffShift` - 45 edges
8. `UserRepository` - 44 edges
9. `BookingService` - 39 edges
10. `DoctorRepository` - 38 edges

## Surprising Connections (you probably didn't know these)
- `Service image uploaded via multipart but previewed from /assets/img/health/` --semantically_similar_to--> `Admin Post Form (POST /admin/manage-news/save, TinyMCE)`  [INFERRED] [semantically similar]
  src/main/resources/templates/admin/service-form.html → src/main/resources/templates/admin/post-form.html
- `Doctor Pending Booking Requests Table (pendingBookings)` --semantically_similar_to--> `patientName/patientPhone fallback to account owner with 'đặt bởi' hint`  [INFERRED] [semantically similar]
  src/main/resources/templates/doctor/booking-requests.html → src/main/resources/templates/doctor/booking-manager.html
- `Doctor footer fragment (vendor JS bundle, assets-admin/js/main.js)` --conceptually_related_to--> `Doctor top navbar fragment header-nav (profile dropdown + embedded AI panel)`  [AMBIGUOUS]
  src/main/resources/templates/doctor/include/footer.html → src/main/resources/templates/doctor/include/header.html
- `Email: Candidate Application Received (HỒ SƠ ỨNG TUYỂN ĐÃ ĐƯỢC GHI NHẬN)` --semantically_similar_to--> `Email: Booking Confirmation (XÁC NHẬN ĐẶT LỊCH THÀNH CÔNG)`  [INFERRED] [semantically similar]
  src/main/resources/templates/email/candidate-confirmation.html → src/main/resources/templates/email/booking-confirmation.html
- `index.html không còn khai báo th:fragment="footer"` --conceptually_related_to--> `Fragment footer dùng chung (HealCare)`  [AMBIGUOUS]
  src/main/resources/templates/user/index.html → src/main/resources/templates/user/include/footer.html

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Mọi nơi khai báo lưới khung giờ khám** — _claude_rules_booking_flow_slot_grid, _claude_rules_booking_flow_all_slots_triplication, _claude_rules_booking_flow_slot_grid_eleven_places, _claude_rules_booking_flow_office_hours_boundary, _claude_rules_ai_assistant_session_mapping_server_side [EXTRACTED 1.00]
- **Ranh giới ca khám / phiên trực và các luật giữ nó** — _claude_rules_supporting_subsystems_ca_kham_vs_phien_truc, _claude_rules_supporting_subsystems_clinic_in_schedule_only, _claude_rules_supporting_subsystems_duty_never_bookable, _claude_rules_supporting_subsystems_duty_start_1730, _claude_rules_booking_flow_slot_grid [EXTRACTED 1.00]
- **Chuỗi cơ chế giữ cho trợ lý AI không nói dối về slot** — _claude_rules_ai_assistant_prompt_5b, _claude_rules_ai_assistant_reason_text, _claude_rules_ai_assistant_suggested_flag, _claude_rules_ai_assistant_resolve_booking_handoff, _claude_rules_ai_assistant_slot_alternatives, _claude_rules_ai_assistant_preview_not_a_decision [EXTRACTED 1.00]
- **Admin page chrome: header, header-nav, sidebar, footer, AI side panel** — src_main_resources_templates_admin_include_header_header, src_main_resources_templates_admin_include_header_headernav, src_main_resources_templates_admin_include_sidebar_sidebar, src_main_resources_templates_admin_include_footer_footer, src_main_resources_templates_admin_include_ai_chat_chatwidget [EXTRACTED 1.00]
- **Doctor booking triage flow: dashboard counters → pending requests → manager → khám** — src_main_resources_templates_doctor_dashboard_dashboard, src_main_resources_templates_doctor_booking_requests_pendingtable, src_main_resources_templates_doctor_booking_manager_bookingtable, src_main_resources_templates_doctor_booking_manager_samedayexamrule [INFERRED 0.85]
- **Superseded template versions kept in-file as HTML comments** — src_main_resources_templates_admin_service_form_servicesaveform, src_main_resources_templates_admin_doctor_form_edit_updateform, src_main_resources_templates_doctor_booking_requests_pendingtable [EXTRACTED 1.00]
- **Luồng khám bệnh & bệnh án điện tử (examination list → EMR form → patient archive)** — src_main_resources_templates_doctor_examination_list_page, src_main_resources_templates_doctor_medical_record_form_page, src_main_resources_templates_doctor_patient_records_page, src_main_resources_templates_doctor_medical_record_form_history_timeline, src_main_resources_templates_doctor_medical_record_form_allergy_alert [INFERRED 0.85]
- **Booking lifecycle transactional email family (confirm / cancel / doctor change / reschedule, shared model attrs + cid QR)** — src_main_resources_templates_email_booking_confirmation_page, src_main_resources_templates_email_booking_cancellation_page, src_main_resources_templates_email_booking_doctor_change_page, src_main_resources_templates_email_booking_rescheduled_page, src_main_resources_templates_email_booking_confirmation_qr_checkin_cid [INFERRED 0.95]
- **Head doctor (trưởng khoa) approval console: department dashboard, duty roster approval, leave approval, shared sidebar** — src_main_resources_templates_head_dashboard_page, src_main_resources_templates_head_duty_roster_page, src_main_resources_templates_head_leave_requests_page, src_main_resources_templates_head_include_sidebar_sidebar [EXTRACTED 1.00]
- **Các bản khai báo trùng lặp của lưới khung giờ 30 phút** — src_main_resources_templates_user_appointment_slot_grid, src_main_resources_templates_receptionist_walk_in_form_page, src_main_resources_templates_user_booking_edit_page, src_main_resources_templates_user_doctor_details_alltimeslots [INFERRED 0.85]
- **Bộ template dùng chung bác sĩ + lễ tân (basePath / sidebarFragment)** — src_main_resources_templates_staff_work_schedule_page, src_main_resources_templates_staff_leave_history_page, src_main_resources_templates_staff_shift_cover_page, src_main_resources_templates_staff_work_schedule_sidebarfragment [EXTRACTED 1.00]
- **Các trang công khai nhúng widget trợ lý AI (user/include/ai-chat :: chat-widget)** — src_main_resources_templates_user_about_page, src_main_resources_templates_user_appointment_page, src_main_resources_templates_user_careers_page, src_main_resources_templates_user_career_details_page, src_main_resources_templates_user_contact_page, src_main_resources_templates_user_department_details_page [EXTRACTED 1.00]
- **Các nơi khai báo lưới khung giờ ở lớp giao diện bệnh nhân (phải đồng bộ với ALL_SLOTS)** — src_main_resources_templates_user_doctors_alltimeslots, src_main_resources_templates_user_index_alltimeslots, src_main_resources_templates_user_doctor_schedule_doctorslotsmap, src_main_resources_templates_user_working_hours_officehours, src_main_resources_templates_user_doctors_officehoursslotgrid [INFERRED 0.85]
- **Nhóm trang mẫu BootstrapMade còn nguyên HTML tĩnh (chưa Thymeleaf hóa)** — src_main_resources_templates_user_faq_page, src_main_resources_templates_user_gallery_page, src_main_resources_templates_user_privacy_page, src_main_resources_templates_user_terms_page, src_main_resources_templates_user_testimonials_page, src_main_resources_templates_user_starter_page_page [INFERRED 0.95]
- **Chuỗi màn hình bệnh nhân đi qua khi đặt lịch: xem bác sĩ → quy định → thanh toán → hồ sơ/bệnh án** — src_main_resources_templates_user_index_page, src_main_resources_templates_user_doctors_page, src_main_resources_templates_user_doctor_schedule_page, src_main_resources_templates_user_medical_process_bookingpolicy, src_main_resources_templates_user_payment_result_page, src_main_resources_templates_user_profile_page, src_main_resources_templates_user_medical_record_detail_page [INFERRED 0.85]

## Communities (121 total, 7 thin omitted)

### Community 0 - "Quản lý bài viết (admin)"
Cohesion: 0.05
Nodes (38): AdminPostController, Controller, GetMapping, Model, MultipartFile, PostMapping, RedirectAttributes, RequestMapping (+30 more)

### Community 1 - "Lịch làm việc & chặn giờ"
Cohesion: 0.07
Nodes (28): Controller, GetMapping, Model, ScheduleInfoController, DoctorBlockTime, Entity, Table, AllArgsConstructor (+20 more)

### Community 2 - "Quản lý người dùng (admin)"
Cohesion: 0.07
Nodes (23): AdminController, Authentication, BindingResult, Controller, GetMapping, Model, PasswordEncoder, PostMapping (+15 more)

### Community 3 - "Danh sách bác sĩ công khai"
Cohesion: 0.06
Nodes (57): doctorSlotsMap (bảng Sáng/Chiều dựng phía server), Trang tra cứu lịch làm việc bác sĩ (/doctor-schedule), allTimeSlots (16 khung giờ hành chính, doctors.html), Hợp đồng /api/bookings/booked-slots (mảng chuỗi "HH:mm - HH:mm"), Không ẩn thẻ bác sĩ khi ngày đang xem hết chỗ, loadAllSchedules(), Lưới khung giờ chỉ trong giờ hành chính, Trang danh sách bác sĩ (/doctors) (+49 more)

### Community 4 - "VNPay & thanh toán đặt lịch"
Cohesion: 0.07
Nodes (30): Configuration, HttpServletRequest, VNPayConfig, BookingController, Authentication, Controller, GetMapping, HttpServletRequest (+22 more)

### Community 5 - "Hồ sơ & lịch hẹn bệnh nhân"
Cohesion: 0.08
Nodes (20): Authentication, Controller, GetMapping, Model, MultipartFile, PostMapping, RedirectAttributes, RequestMapping (+12 more)

### Community 6 - "Danh mục dịch vụ (admin)"
Cohesion: 0.09
Nodes (24): AdminServiceController, Controller, GetMapping, Model, MultipartFile, PostMapping, RedirectAttributes, RequestMapping (+16 more)

### Community 7 - "Màn hình hàng đợi khám"
Cohesion: 0.08
Nodes (23): DoctorExaminationController, Authentication, Controller, GetMapping, Model, RequestMapping, Controller, GetMapping (+15 more)

### Community 8 - "Quầy lễ tân"
Cohesion: 0.07
Nodes (47): Trang Tất cả lịch hẹn (lễ tân), Trang Tổng quan quầy lễ tân, Fragment footer lễ tân (bootstrap bundle + simple-datatables + main.js), Fragment header lễ tân (head(title) + header-nav với dropdown hồ sơ), Fragment sidebar lễ tân (Tiếp đón / Điều phối lịch / Cá nhân), Trang Hàng chờ khám (đẩy xuống cuối / hoàn tác), confirmTransfer() — chặn submit khi chưa chọn bác sĩ tiếp nhận, Trang Hủy / Dời lịch hàng loạt (bác sĩ nghỉ đột xuất) (+39 more)

### Community 9 - "Endpoint AI cho bác sĩ"
Cohesion: 0.09
Nodes (17): DoctorAiController, DeleteMapping, GetMapping, PostMapping, RequestMapping, ResponseEntity, RestController, BookingStatus (+9 more)

### Community 10 - "Script chat frontend"
Cohesion: 0.11
Nodes (38): appendMessage(), loadWelcomeMessage(), openChat(), sendMessage(), updateLiveStats(), addThirtyMinutes(), appendMessage(), buildAlternativeContext() (+30 more)

### Community 11 - "Repository & tra cứu User"
Cohesion: 0.09
Nodes (16): Override, Query, UserRepository, Authentication, CustomUserDetailsService, Override, Transactional, UserDetails (+8 more)

### Community 12 - "Giao diện lịch làm việc"
Cohesion: 0.12
Nodes (38): activateTab(), bindNavigation(), bindSearch(), bindTabs(), boundsForDay(), buildDayColumn(), buildDayHeader(), buildEventBlock() (+30 more)

### Community 13 - "API khung giờ & thanh toán"
Cohesion: 0.09
Nodes (19): BookingApi, GetMapping, RequestMapping, ResponseEntity, RestController, Controller, GetMapping, Model (+11 more)

### Community 14 - "Form bác sĩ & tuyển dụng (admin)"
Cohesion: 0.10
Nodes (41): Hidden user.id / user.roles carry-through on doctor edit, Admin Doctor Edit Form (POST /admin/manage-doctor/update), Admin Doctor Create Form (POST /admin/manage-doctor/save), Admin Doctor List Table (listDoctors), Admin AI side panel fragment (chat-widget, Trợ lý Điều hành), meditrust-voice.js must load before admin-ai-chat.js, Admin footer fragment (vendor script bundle), AI widget embedded inside header-nav so it always exists on the page (+33 more)

### Community 15 - "Hồ sơ bệnh án & chat bác sĩ"
Cohesion: 0.08
Nodes (37): isToday guard on 'Thực hiện Khám' button (only today's patients examinable), Late-patient queue badge (lateMarkedAt highlight), Doctor Examination List Page (Lịch khám bệnh), Legacy doctor AI floating chat-box fragment (ai-chat :: chat-widget, loads doctor-ai-chat.js, no voice layer), Doctor AI side-panel chat widget fragment (ai-chat-doctor :: chat-widget), Doctor footer fragment (vendor JS bundle, assets-admin/js/main.js), Doctor head fragment header(title) (admin CSS vendor bundle), Doctor top navbar fragment header-nav (profile dropdown + embedded AI panel) (+29 more)

### Community 16 - "Entity StaffShift"
Cohesion: 0.11
Nodes (15): ApprovalStatus, APPROVED, CANCELED, PENDING, REJECTED, Entity, Getter, NoArgsConstructor (+7 more)

### Community 17 - "Hành động dashboard bác sĩ"
Cohesion: 0.12
Nodes (17): DoctorDashboardController, Authentication, Controller, GetMapping, Model, PostMapping, RedirectAttributes, RequestMapping (+9 more)

### Community 18 - "API thông báo nhân sự"
Cohesion: 0.13
Nodes (8): Authentication, GetMapping, RequestMapping, ResponseEntity, RestController, StaffScheduleApiController, CurrentUserService, StaffScheduleService

### Community 19 - "Yêu cầu thay ca"
Cohesion: 0.14
Nodes (13): Entity, Getter, NoArgsConstructor, Setter, Table, ShiftCoverRequest, EntityGraph, Query (+5 more)

### Community 20 - "Dịch vụ xuất PDF"
Cohesion: 0.18
Nodes (7): Document, Font, Paragraph, PdfPTable, Override, PdfExportServiceImpl, QRCodeGenerator

### Community 21 - "Endpoint gợi ý khung giờ AI"
Cohesion: 0.14
Nodes (6): AiController, DaySlots, GetMapping, RequestMapping, RestController, SlotLock

### Community 22 - "Entity & repository Doctor"
Cohesion: 0.15
Nodes (14): Doctor, AllArgsConstructor, Entity, Getter, NoArgsConstructor, Setter, Table, DoctorRepository (+6 more)

### Community 23 - "Lưu phiên chat AI"
Cohesion: 0.12
Nodes (18): Modifying, PreUpdate, AiChatSession, AllArgsConstructor, Entity, Getter, NoArgsConstructor, PrePersist (+10 more)

### Community 24 - "Service lịch làm việc nhân sự"
Cohesion: 0.17
Nodes (3): Override, Transactional, StaffScheduleServiceImpl

### Community 25 - "Hợp đồng LeaveService"
Cohesion: 0.10
Nodes (9): AllArgsConstructor, Entity, Getter, NoArgsConstructor, Setter, Table, User, LeaveService (+1 more)

### Community 26 - "Entity MedicalRecord"
Cohesion: 0.15
Nodes (15): AllArgsConstructor, Entity, Getter, NoArgsConstructor, Setter, Table, MedicalRecord, MedicalRecordStatus (+7 more)

### Community 27 - "Chế độ gọi rảnh tay"
Cohesion: 0.26
Nodes (26): addCallButton(), armSilenceTimer(), buildOverlay(), chat(), clearSilenceTimer(), confirmBooking(), describeLoadVoice(), describeSlotFull() (+18 more)

### Community 28 - "Lõi BookingService"
Cohesion: 0.20
Nodes (3): ReentrantLock, BookingServiceImpl, Override

### Community 29 - "Quản lý chuyên khoa"
Cohesion: 0.14
Nodes (12): AdminDepartmentController, Controller, GetMapping, Model, PostMapping, RedirectAttributes, RequestMapping, DepartmentController (+4 more)

### Community 30 - "Phê duyệt của trưởng khoa"
Cohesion: 0.21
Nodes (8): HeadApprovalController, Authentication, Controller, GetMapping, Model, PostMapping, RedirectAttributes, RequestMapping

### Community 31 - "Entity LeaveRequest"
Cohesion: 0.15
Nodes (10): Entity, Getter, NoArgsConstructor, Setter, Table, LeaveRequest, EntityGraph, Query (+2 more)

### Community 32 - "API trợ lý bác sĩ"
Cohesion: 0.14
Nodes (14): DoctorAssistantController, GetMapping, PostMapping, PreAuthorize, RequestMapping, ResponseEntity, RestController, AiResponse (+6 more)

### Community 33 - "Hồ sơ bệnh án bác sĩ soạn"
Cohesion: 0.19
Nodes (9): DoctorMedicalRecordController, Authentication, Controller, GetMapping, Model, PostMapping, RedirectAttributes, RequestMapping (+1 more)

### Community 34 - "Entity JobPosting"
Cohesion: 0.17
Nodes (12): AllArgsConstructor, Entity, Getter, NoArgsConstructor, PrePersist, Setter, Table, JobPosting (+4 more)

### Community 35 - "Module giọng nói dùng chung"
Cohesion: 0.21
Nodes (22): attach(), createRecognition(), ensureConsent(), friendlyRecognitionError(), hasConsent(), humanizeSchedule(), injectStyles(), isRecognitionSupported() (+14 more)

### Community 36 - "Tính quota nghỉ phép"
Cohesion: 0.13
Nodes (8): AllArgsConstructor, Data, NoArgsConstructor, LeaveBalanceDTO, WorkCondition, EXTRA_HEAVY, HEAVY, NORMAL

### Community 37 - "Hồ sơ & đơn thuốc bệnh nhân"
Cohesion: 0.16
Nodes (16): Authentication, Controller, GetMapping, Model, RedirectAttributes, RequestMapping, UserMedicalRecordController, AllArgsConstructor (+8 more)

### Community 38 - "Luật trung thực của trợ lý AI"
Cohesion: 0.12
Nodes (21): buildAlternativeContext ghép danh sách đã gợi ý vào prompt lượt đó, lastHandoffDate nhớ ngày của handoff vừa giải, sameTimeDoctors xếp theo nearbyLoad rồi dayLoad, OFF_DUTY cố tình xếp trên BOOKED, Số thứ tự ánh xạ sang HƯỚNG, không phải vị trí trong danh sách, otherTimes có thể ở ngày khác, availableSlots chỉ là preview 4 slot của ngày gần nhất, Mục 5B — AI không được nhận vơ đã ghi nhận/giữ chỗ (+13 more)

### Community 39 - "Role & phân lớp kiến trúc"
Cohesion: 0.10
Nodes (21): CurrentUserService — cách đúng để lấy user hiện tại, Principal có thể là UserDetails hoặc OAuth2User, Năm role điều khiển toàn app, Trưởng khoa là bác sĩ có thêm role, không phải loại tài khoản riêng, StaffProfile.headOfDepartment quyết định khoa được quản, SecurityConfig — nguồn sự thật duy nhất cho phân quyền URL, successHandler và OAuth2LoginSuccessHandler phải sửa cùng nhau, Phép được duyệt làm bác sĩ biến mất khỏi mọi chỗ tra slot (+13 more)

### Community 40 - "LeavePolicy & loại ca"
Cohesion: 0.15
Nodes (9): MonthDay, LeavePolicy, ShiftType, CA_CHIEU, CA_SANG, HOI_CHAN, TRUC_12H_DEM, TRUC_24H (+1 more)

### Community 41 - "Controller bác sĩ (admin)"
Cohesion: 0.21
Nodes (10): AdminDoctorController, BindingResult, Controller, GetMapping, Model, MultipartFile, PasswordEncoder, PostMapping (+2 more)

### Community 42 - "API & DTO bác sĩ"
Cohesion: 0.14
Nodes (8): DoctorApiController, GetMapping, RequestMapping, ResponseEntity, RestController, DoctorDTO, Data, DoctorService

### Community 43 - "Lớp cha lịch làm việc"
Cohesion: 0.33
Nodes (6): Authentication, GetMapping, Model, PostMapping, RedirectAttributes, StaffWorkScheduleController

### Community 44 - "Lưới khung giờ & ca khám"
Cohesion: 0.14
Nodes (20): normalizeTimeHint đòi dấu hiệu giờ rõ ràng, Mục 1 — giờ làm việc của cả phòng khám, slotStartTime — so sánh giờ yêu cầu với giờ BẮT ĐẦU của slot, ALL_SLOTS bị khai báo ba lần trong Java, ScheduleRepository.findEffective / findEffectiveOn, BookingService.isSlotWithinWorkingHours, LeavePolicy.OFFICE_START / OFFICE_END, Schedule là theo tuần, không phải mẫu tuần toàn cục (+12 more)

### Community 45 - "Thông tin user OAuth2"
Cohesion: 0.16
Nodes (5): OAuth2User, CustomOAuth2User, GrantedAuthority, Override, OAuth2UserInfo

### Community 46 - "Controller tuyển dụng (admin)"
Cohesion: 0.20
Nodes (8): AdminJobController, Controller, GetMapping, Model, PostMapping, RedirectAttributes, RequestMapping, JobPostingService

### Community 47 - "Bệnh nhân sửa lịch hẹn"
Cohesion: 0.19
Nodes (13): Authentication, Controller, GetMapping, Model, PostMapping, RedirectAttributes, RequestMapping, Transactional (+5 more)

### Community 48 - "Khởi tạo dữ liệu seed"
Cohesion: 0.17
Nodes (7): CommandLineRunner, DataInitializer, Component, Override, PasswordEncoder, DoctorSeedData, SeedDoctor

### Community 49 - "Seed role & tài khoản"
Cohesion: 0.16
Nodes (10): AllArgsConstructor, Entity, Getter, NoArgsConstructor, Setter, Table, Role, RoleRepository (+2 more)

### Community 50 - "LeaveServiceImpl"
Cohesion: 0.27
Nodes (4): LeaveRequestDTO, Override, Transactional, LeaveServiceImpl

### Community 51 - "Vai trò trực & kết quả"
Cohesion: 0.16
Nodes (9): AllArgsConstructor, Data, NoArgsConstructor, ShiftRegisterResultDTO, DutyRole, TRUC_CAN_LAM_SANG, TRUC_HAU_CAN, TRUC_LAM_SANG (+1 more)

### Community 52 - "Entity Department"
Cohesion: 0.20
Nodes (11): Department, AllArgsConstructor, Entity, Getter, NoArgsConstructor, Setter, Table, DepartmentRepository (+3 more)

### Community 53 - "Entity StaffProfile"
Cohesion: 0.21
Nodes (9): Entity, Getter, NoArgsConstructor, Setter, Table, StaffProfile, EntityGraph, Repository (+1 more)

### Community 54 - "Controller AI cho admin"
Cohesion: 0.23
Nodes (8): AdminAiController, DeleteMapping, GetMapping, PostMapping, PreAuthorize, RequestMapping, ResponseEntity, RestController

### Community 56 - "Dựng sự kiện lịch"
Cohesion: 0.19
Nodes (4): AllArgsConstructor, Data, NoArgsConstructor, ScheduleEventDTO

### Community 57 - "Bộ nhớ AI & luật giọng nói"
Cohesion: 0.13
Nodes (15): Bộ nhớ hội thoại trong AiChatSession, is_emergency làm đỏ overlay và bỏ luồng đặt lịch, Job @Scheduled dọn session khách quá 7 ngày, Bán song công là bắt buộc, Schema JSON 9 keys là một hợp đồng, Hồ sơ bệnh án COMPLETED gần nhất tiêm vào ngữ cảnh, onReply nổ đúng một lần mỗi lượt, kể cả khi lỗi, Trợ lý riêng cho admin và bác sĩ (+7 more)

### Community 58 - "Xử lý đăng nhập OAuth2"
Cohesion: 0.20
Nodes (12): HttpServletResponse, SimpleUrlAuthenticationSuccessHandler, AuthProvider, FACEBOOK, GOOGLE, LOCAL, Authentication, Component (+4 more)

### Community 59 - "Loại nghỉ phép"
Cohesion: 0.14
Nodes (8): LeaveType, KHONG_LUONG_THOA_THUAN, NGHI_BU_TRUC, OM_DAU, PHEP_NAM, THAI_SAN, VIEC_RIENG_CO_LUONG, VIEC_RIENG_KHONG_LUONG

### Community 60 - "Trang chủ công khai"
Cohesion: 0.29
Nodes (7): HomeController, Controller, GetMapping, JavaMailSender, Model, PostMapping, RedirectAttributes

### Community 61 - "Quy ước giọng nói & ngôn ngữ"
Cohesion: 0.14
Nodes (13): MediTrustVoice — module dùng chung cho cả 3 khung chat, speech_reply — biến thể đọc thành tiếng của ai_reply, toSpeechText — biến HTML/Markdown thành tiếng Việt đọc được, Giọng nói chạy hoàn toàn trong trình duyệt, Xưng hô: trợ lý là 'em', bệnh nhân là 'anh/chị', Không có bước build frontend, maven-resources-plugin ghim UTF-8, SpeechRecognition chỉ có ở Chrome/Edge và secure context (+5 more)

### Community 62 - "Dịch vụ gửi email"
Cohesion: 0.32
Nodes (5): Async, EmailServiceImpl, JavaMailSender, Override, TemplateEngine

### Community 63 - "In phiếu thu & đơn thuốc"
Cohesion: 0.31
Nodes (6): Controller, GetMapping, RequestMapping, ResponseEntity, ReceptionistPrintController, PdfExportService

### Community 64 - "Tuyển dụng & ứng tuyển"
Cohesion: 0.21
Nodes (7): CareerController, Controller, GetMapping, Model, MultipartFile, PostMapping, RedirectAttributes

### Community 65 - "Entity Candidate"
Cohesion: 0.22
Nodes (10): Candidate, AllArgsConstructor, Entity, Getter, NoArgsConstructor, PrePersist, Setter, Table (+2 more)

### Community 66 - "Luật seed & uploads"
Cohesion: 0.17
Nodes (13): ID khoa 21 = Cấp cứu, 22 = Y học gia đình, Danh sách khoa tiêm vào prompt lúc chạy, Mẫu ensureReceptionistAccount cho role thêm sau, Bẫy @AllArgsConstructor dùng theo vị trí trong DataInitializer, DoctorSeedData tách bảng dữ liệu khỏi DataInitializer, DataInitializer chỉ seed khi bảng users rỗng, Một phong cách ảnh chân dung bác sĩ duy nhất, Các khối ensureXxx() chạy ngoài guard seed (+5 more)

### Community 67 - "Entity AiRule"
Cohesion: 0.26
Nodes (10): JpaRepository, AiRule, AllArgsConstructor, Entity, Getter, NoArgsConstructor, Setter, Table (+2 more)

### Community 68 - "Xét ứng viên (admin)"
Cohesion: 0.29
Nodes (6): AdminCandidateController, Controller, GetMapping, Model, RedirectAttributes, RequestMapping

### Community 69 - "Đặt lịch tại quầy"
Cohesion: 0.26
Nodes (9): Controller, GetMapping, Model, PasswordEncoder, PostMapping, RedirectAttributes, RequestMapping, Transactional (+1 more)

### Community 70 - "Luật thanh toán & cấu hình"
Cohesion: 0.18
Nodes (12): Chuỗi model dự phòng OpenRouter, Nhánh BANK_TRANSFER — chuyển sang /checkout-qr, processAppointment phân nhánh theo paymentMethod, paymentStatus là String riêng, không phải enum, Chuỗi vnp_OrderInfo là load-bearing, App chạy ở cổng 8090, VNPayConfig và application.properties chỉ dùng cho dev, application.properties giữ toàn bộ cấu hình và khóa (+4 more)

### Community 71 - "Luật đồng thời đặt lịch"
Cohesion: 0.18
Nodes (12): Đặt hộ qua patientName / patientPhone, Booking trỏ tới Doctor và lưu ảnh chụp giá trong bookingPrice, Khóa chỉ trong một tiến trình, bookingPrice và paymentStatus cố tình không bị sửa khi dời lịch, queueOrder / lateMarkedAt điều khiển hàng đợi khám, reassign(bookingId, newDoctorId) dùng lại cùng slotLocks, rescheduleByUser — bệnh nhân tự dời lịch, rescheduleCount chỉ tăng khi slot thực sự đổi (+4 more)

### Community 72 - "Lý do nghỉ việc riêng"
Cohesion: 0.17
Nodes (7): getLeaveType(), PersonalLeaveReason, CON_KET_HON, KET_HON, NGUOI_THAN_KET_HON, TANG_HO_HANG, TANG_NGUOI_THAN

### Community 73 - "Controller đăng nhập & đăng ký"
Cohesion: 0.27
Nodes (7): AuthController, Controller, GetMapping, Model, PostMapping, Data, RegisterDTO

### Community 74 - "Thống kê dashboard admin"
Cohesion: 0.33
Nodes (6): AdminDashboardSummaryDTO, FinancialStats, Data, OperationalStats, QualityAndHRStats, AdminDashboardService

### Community 75 - "Nghỉ nửa ngày"
Cohesion: 0.17
Nodes (5): Data, HalfDaySession, CHIEU, NONE, SANG

### Community 76 - "Entity Allergy"
Cohesion: 0.27
Nodes (9): Allergy, AllArgsConstructor, Entity, Getter, NoArgsConstructor, Setter, Table, AllergyRepository (+1 more)

### Community 77 - "MedicalAddendum"
Cohesion: 0.27
Nodes (9): AllArgsConstructor, Entity, Getter, NoArgsConstructor, Setter, Table, MedicalAddendum, Repository (+1 more)

### Community 78 - "MedicalAttachment"
Cohesion: 0.27
Nodes (9): AllArgsConstructor, Entity, Getter, NoArgsConstructor, Setter, Table, MedicalAttachment, Repository (+1 more)

### Community 79 - "Entity VitalSign"
Cohesion: 0.27
Nodes (9): AllArgsConstructor, Entity, Getter, NoArgsConstructor, Setter, Table, VitalSign, Repository (+1 more)

### Community 80 - "Hàm phân tích câu của AI"
Cohesion: 0.20
Nodes (11): buildTodayBlock tiêm ngày hôm nay vào cuối prompt, Tham số doctorId đẩy bác sĩ được gọi tên lên đầu trước khi .limit(3), doctorNotFound phải được nói ra, không được thay người, extractDateHint hiểu tên thứ và 'tuần sau', extractDoctorName neo phần cắt vào cuối chuỗi, extractSessionHint trả morning / afternoon / evening, pickBestDoctorMatch khớp theo biên từ, Ánh xạ buổi → slot nằm ở server, không ở trình duyệt (+3 more)

### Community 81 - "Controller đặt lịch (admin)"
Cohesion: 0.33
Nodes (6): AdminBookingController, Controller, GetMapping, Model, RedirectAttributes, RequestMapping

### Community 82 - "Controller chat AI & khóa slot"
Cohesion: 0.22
Nodes (6): DeleteMapping, PostMapping, ResponseEntity, Scheduled, ChatRequest, Data

### Community 83 - "API thống kê dashboard"
Cohesion: 0.31
Nodes (8): DashboardApiController, GetMapping, RequestMapping, RestController, DailyBookingStatsDTO, AllArgsConstructor, Data, NoArgsConstructor

### Community 84 - "DTO request/response AI"
Cohesion: 0.31
Nodes (8): AiMessage, AllArgsConstructor, Data, NoArgsConstructor, AiRequest, AllArgsConstructor, Data, NoArgsConstructor

### Community 85 - "Quy ước tài liệu & template"
Cohesion: 0.20
Nodes (10): Template và fragment dùng chung theo từng khu vực, Định dạng một dòng cho mỗi đơn vị công việc, Bắt buộc cập nhật tài liệu trong cùng thay đổi, Bảng ánh xạ chủ đề → rule file sở hữu, @RestController chỉ cho nhu cầu bất đồng bộ thật sự, Render phía server bằng Thymeleaf, AdminDashboardService + DashboardApiController, Yêu cầu luôn cập nhật tài liệu cùng lúc với code (+2 more)

### Community 86 - "SecurityConfig"
Cohesion: 0.36
Nodes (7): DaoAuthenticationProvider, HttpSecurity, SecurityFilterChain, Bean, Configuration, PasswordEncoder, SecurityConfig

### Community 87 - "Quản lý lịch hẹn bác sĩ"
Cohesion: 0.36
Nodes (6): DoctorBookingManagerController, Authentication, Controller, GetMapping, Model, RequestMapping

### Community 88 - "Danh sách lịch hẹn lễ tân"
Cohesion: 0.33
Nodes (6): Controller, GetMapping, Model, RedirectAttributes, RequestMapping, ReceptionistBookingController

### Community 89 - "Chi tiết bác sĩ công khai"
Cohesion: 0.33
Nodes (5): DoctorController, Controller, GetMapping, Model, RequestMapping

### Community 90 - "Giới hạn hủy & dời lịch"
Cohesion: 0.22
Nodes (9): cancelWithRefund — nơi duy nhất hủy booking, MAX_RESCHEDULE_TIMES = 2, MIN_HOURS_BEFORE_CHANGE = 24, whyCannotCancel — nguồn sự thật duy nhất cho quyền hủy, whyCannotReschedule — uỷ quyền cho whyCannotCancel rồi cộng thêm quota, ClinicRegistrationTask — hai cron Chủ nhật, countAffectedBookings cảnh báo trên màn duyệt, EmailServiceImpl gửi mail HTML bất đồng bộ (+1 more)

### Community 91 - "Hồ sơ bệnh án (admin)"
Cohesion: 0.42
Nodes (6): AdminMedicalRecordController, Controller, GetMapping, Model, RedirectAttributes, RequestMapping

### Community 92 - "Yêu cầu đặt lịch tới bác sĩ"
Cohesion: 0.42
Nodes (6): DoctorBookingRequestController, Authentication, Controller, GetMapping, Model, RequestMapping

### Community 93 - "Route lịch làm việc bác sĩ"
Cohesion: 0.33
Nodes (5): DoctorWorkScheduleController, Controller, GetMapping, Override, RequestMapping

### Community 94 - "Thông tin OAuth2 Facebook"
Cohesion: 0.31
Nodes (3): FacebookOAuth2UserInfo, Override, SuppressWarnings

### Community 95 - "CustomOAuth2UserService"
Cohesion: 0.39
Nodes (6): DefaultOAuth2UserService, OAuth2UserRequest, CustomOAuth2UserService, OAuth2User, Override, PasswordEncoder

### Community 96 - "Điểm khởi động ứng dụng"
Cohesion: 0.39
Nodes (5): EnableAsync, SpringBootApplication, BookingHealthyApplication, Bean, RestTemplate

### Community 99 - "Cron đăng ký ca khám"
Cohesion: 0.36
Nodes (3): ClinicRegistrationTask, Component, Scheduled

### Community 100 - "Cấu hình tài nguyên MVC"
Cohesion: 0.43
Nodes (5): ResourceHandlerRegistry, Configuration, Override, WebConfig, WebMvcConfigurer

### Community 101 - "Dashboard lễ tân"
Cohesion: 0.48
Nodes (5): Controller, GetMapping, Model, RequestMapping, ReceptionistDashboardController

### Community 102 - "Route lịch làm việc lễ tân"
Cohesion: 0.43
Nodes (4): Controller, Override, RequestMapping, ReceptionistWorkScheduleController

### Community 103 - "Cấu hình PasswordEncoder"
Cohesion: 0.53
Nodes (4): AppConfig, Bean, Configuration, PasswordEncoder

### Community 104 - "Dọn booking hết hạn"
Cohesion: 0.47
Nodes (3): BookingCleanupTask, Component, Scheduled

### Community 106 - "Bẫy build & kiểm thử"
Cohesion: 0.40
Nodes (5): CSRF tắt toàn cục, Maven wrapper + Java 21, Không bật --enable-preview, Controller bắt Exception rộng, printStackTrace, đẩy thông báo qua flash attribute, Chưa có bộ test nào

### Community 107 - "Enum CandidateStatus"
Cohesion: 0.40
Nodes (4): CandidateStatus, APPROVED, PENDING, REJECTED

### Community 108 - "Cấu hình lịch chạy"
Cohesion: 0.83
Nodes (3): EnableScheduling, Configuration, SchedulerConfig

### Community 109 - "Script chat admin"
Cohesion: 1.00
Nodes (3): appendMessage(), saveCache(), sendMessage()

### Community 114 - "Trang lỗi"
Cohesion: 0.67
Nodes (3): Error page 403 (Không có quyền truy cập, Thymeleaf-wired), Error page 404 (unconverted BootstrapMade demo template: relative asset paths, no th: namespace, dummy nav/footer), Error page 500 (Lỗi hệ thống, Thymeleaf-wired)

### Community 115 - "Trang cơ hội nghề nghiệp"
Cohesion: 1.00
Nodes (3): Trang Chi tiết tin tuyển dụng + form ứng tuyển (POST /career-apply), Footer lấy từ ~{user/index :: footer} thay vì user/include/footer (lệch chuẩn), Trang Cơ hội nghề nghiệp (danh sách JobPosting)

## Ambiguous Edges - Review These
- `Doctor footer fragment (vendor JS bundle, assets-admin/js/main.js)` → `Doctor top navbar fragment header-nav (profile dropdown + embedded AI panel)`  [AMBIGUOUS]
  src/main/resources/templates/doctor/include/footer.html · relation: conceptually_related_to
- `Trang Giới thiệu (HealCare — sứ mệnh, công nghệ cốt lõi, đội ngũ)` → `Trang Liên hệ (form POST /contact + bản đồ nhúng)`  [AMBIGUOUS]
  src/main/resources/templates/user/about.html · relation: conceptually_related_to
- `allTimeSlots (16 khung giờ hành chính, doctors.html)` → `Giờ hành chính công bố: T2–T6 07:30–11:30 / 13:30–17:30, T7 sáng, CN chỉ trực cấp cứu`  [AMBIGUOUS]
  src/main/resources/templates/user/working-hours.html · relation: conceptually_related_to
- `index.html không còn khai báo th:fragment="footer"` → `Fragment footer dùng chung (HealCare)`  [AMBIGUOUS]
  src/main/resources/templates/user/index.html · relation: conceptually_related_to
- `Quy định đặt lịch: trả trước 100%, quy tắc 24 giờ, hoàn 100% vào Ví` → `Trang Terms (mẫu BootstrapMade, chưa Thymeleaf hóa)`  [AMBIGUOUS]
  src/main/resources/templates/user/terms.html · relation: semantically_similar_to
- `Trang danh sách dịch vụ (/services)` → `Fragment chat-widget (khung chat AI bệnh nhân)`  [AMBIGUOUS]
  src/main/resources/templates/user/services.html · relation: conceptually_related_to

## Knowledge Gaps
- **126 isolated node(s):** `com.bookinghealthy:booking-healthy`, `PENDING`, `APPROVED`, `REJECTED`, `CANCELED` (+121 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **7 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `Doctor footer fragment (vendor JS bundle, assets-admin/js/main.js)` and `Doctor top navbar fragment header-nav (profile dropdown + embedded AI panel)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **What is the exact relationship between `Trang Giới thiệu (HealCare — sứ mệnh, công nghệ cốt lõi, đội ngũ)` and `Trang Liên hệ (form POST /contact + bản đồ nhúng)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **What is the exact relationship between `allTimeSlots (16 khung giờ hành chính, doctors.html)` and `Giờ hành chính công bố: T2–T6 07:30–11:30 / 13:30–17:30, T7 sáng, CN chỉ trực cấp cứu`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **What is the exact relationship between `index.html không còn khai báo th:fragment="footer"` and `Fragment footer dùng chung (HealCare)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **What is the exact relationship between `Quy định đặt lịch: trả trước 100%, quy tắc 24 giờ, hoàn 100% vào Ví` and `Trang Terms (mẫu BootstrapMade, chưa Thymeleaf hóa)`?**
  _Edge tagged AMBIGUOUS (relation: semantically_similar_to) - confidence is low._
- **What is the exact relationship between `Trang danh sách dịch vụ (/services)` and `Fragment chat-widget (khung chat AI bệnh nhân)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **Why does `User` connect `Hợp đồng LeaveService` to `Quản lý bài viết (admin)`, `Quản lý người dùng (admin)`, `VNPay & thanh toán đặt lịch`, `Hồ sơ & lịch hẹn bệnh nhân`, `Endpoint AI cho bác sĩ`, `Repository & tra cứu User`, `API khung giờ & thanh toán`, `Entity StaffShift`, `API thông báo nhân sự`, `Yêu cầu thay ca`, `Entity & repository Doctor`, `Lưu phiên chat AI`, `Service lịch làm việc nhân sự`, `Lõi BookingService`, `Phê duyệt của trưởng khoa`, `Entity LeaveRequest`, `API trợ lý bác sĩ`, `Tính quota nghỉ phép`, `Hồ sơ & đơn thuốc bệnh nhân`, `LeavePolicy & loại ca`, `Controller bác sĩ (admin)`, `Lớp cha lịch làm việc`, `Bệnh nhân sửa lịch hẹn`, `Khởi tạo dữ liệu seed`, `Seed role & tài khoản`, `LeaveServiceImpl`, `Vai trò trực & kết quả`, `Entity StaffProfile`, `ReceptionServiceImpl`, `Dựng sự kiện lịch`, `Xử lý đăng nhập OAuth2`, `Đặt lịch tại quầy`, `Entity Allergy`?**
  _High betweenness centrality (0.172) - this node is a cross-community bridge._