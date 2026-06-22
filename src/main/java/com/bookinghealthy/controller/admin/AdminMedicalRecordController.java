package com.bookinghealthy.controller.admin;

import com.bookinghealthy.model.Booking;
import com.bookinghealthy.model.BookingStatus;
import com.bookinghealthy.model.MedicalRecord;
import com.bookinghealthy.repository.BookingRepository;
import com.bookinghealthy.repository.MedicalRecordRepository;
import com.bookinghealthy.service.MedicalRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/medical-records")
public class AdminMedicalRecordController {

    @Autowired
    private MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private MedicalRecordService medicalRecordService; // Dùng Service hoặc Repo đều được

    @Autowired
    private BookingRepository bookingRepository;

    // 1. TRANG DANH SÁCH BỆNH ÁN TOÀN HỆ THỐNG
    @GetMapping("")
    public String listAllMedicalRecords(Model model) {
        // Lấy tất cả bệnh án (Sắp xếp mới nhất lên đầu nếu muốn, ở đây dùng findAll mặc định)
        List<MedicalRecord> records = medicalRecordRepository.findAll();

        model.addAttribute("listRecords", records);
        return "admin/medical-record-list";
    }

    // 2. XEM CHI TIẾT BỆNH ÁN (ADMIN VIEW)
    @GetMapping("/view/{id}")
    public String viewMedicalRecord(@PathVariable("id") Long id, Model model, RedirectAttributes ra) {
        // Tìm bệnh án theo ID của bảng MedicalRecord (không phải BookingID)
        MedicalRecord record = medicalRecordRepository.findById(id)
                .orElse(null);

        if (record == null) {
            ra.addFlashAttribute("errorMessage", "Không tìm thấy hồ sơ bệnh án.");
            return "redirect:/admin/medical-records";
        }

        // Lấy thông tin booking liên quan để hiển thị tên BN, BS
        Booking booking = record.getBooking();

        model.addAttribute("record", record);
        model.addAttribute("booking", booking);

        // Cấu hình giao diện (Tương tự Doctor/User)
        model.addAttribute("role", "ADMIN"); // Đánh dấu là ADMIN đang xem
        model.addAttribute("backLink", "/admin/medical-records"); // Nút quay lại trỏ về danh sách admin

        // Tái sử dụng giao diện chi tiết (Code 1 lần dùng 3 nơi)
        return "user/medical-record-detail";
    }
}