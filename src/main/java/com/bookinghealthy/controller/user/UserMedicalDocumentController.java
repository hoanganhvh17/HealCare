package com.bookinghealthy.controller.user;

import com.bookinghealthy.dto.ImageAnalysis;
import com.bookinghealthy.model.ExternalMedicalRecord;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.ExternalMedicalRecordRepository;
import com.bookinghealthy.service.AiService;
import com.bookinghealthy.service.ChatImageService;
import com.bookinghealthy.service.CurrentUserService;
import com.bookinghealthy.service.ExternalMedicalRecordService;
import com.bookinghealthy.service.FileStorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Bệnh nhân tải lên hồ sơ bệnh án mang từ nơi khác tới, trong tab "Hồ sơ y tế" của trang cá nhân.
 *
 * Tách khỏi {@code ProfileController} theo đúng cách {@code UserAllergyController} đã tách — lớp
 * kia đã lo hồ sơ + lịch sử khám + đổi mật khẩu.
 */
@Controller
@RequestMapping("/user/medical-document")
public class UserMedicalDocumentController {

    /** Quay lại đúng tab "Hồ sơ y tế"; JS sẵn có trong profile.html mở tab theo hash. */
    private static final String BACK_TO_TAB = "redirect:/user/profile#medical-records";

    @Autowired private CurrentUserService currentUserService;
    @Autowired private ExternalMedicalRecordService externalMedicalRecordService;
    @Autowired private ExternalMedicalRecordRepository externalMedicalRecordRepository;
    @Autowired private FileStorageService fileStorageService;
    @Autowired private ChatImageService chatImageService;
    @Autowired private AiService aiService;

    @PostMapping("/upload")
    public String upload(@RequestParam("title") String title,
                         @RequestParam(value = "patientNote", required = false) String patientNote,
                         @RequestParam("file") MultipartFile file,
                         Authentication authentication,
                         RedirectAttributes ra) {
        User user = currentUserService.require(authentication);
        try {
            ExternalMedicalRecord saved = externalMedicalRecordService.upload(
                    user.getId(), title, patientNote, file);

            // MỌI đường gọi model tính tiền đều phải qua hạn mức. Trước đây chỉ nhánh
            // /chat-upload kiểm, nên bệnh nhân tải lên từ trang hồ sơ là đốt lượt gọi AI
            // không giới hạn còn bộ đếm ai_image_usage đứng yên — tức phép kiểm ở khung chat
            // soi mãi một con số không bao giờ tăng.
            String overQuota = chatImageService.whyCannotAnalyzeImage(user);
            if (overQuota != null) {
                // Tệp VẪN được giữ lại, chỉ hoãn phần đọc bằng AI. Hồ sơ nằm ở PENDING và
                // bệnh nhân bấm "Phân tích lại" được vào ngày hôm sau.
                ra.addFlashAttribute("successMessage",
                        "Đã tải hồ sơ lên. " + overQuota);
                return BACK_TO_TAB;
            }
            chatImageService.countOneAnalysis(user);

            // Phân tích chạy NGAY và ĐỒNG BỘ: bệnh nhân vừa bấm Tải lên thì đang chờ trước màn
            // hình, một trang nạp lại kèm kết quả là phản hồi rõ ràng nhất. Bản thân analyze()
            // tự nuốt mọi lỗi vào aiStatus nên không có đường nào làm hỏng lượt tải lên.
            externalMedicalRecordService.analyze(saved.getId());
            ra.addFlashAttribute("successMessage", "Đã tải hồ sơ lên và phân tích xong.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("errorMessage", "Không tải được hồ sơ lên. Vui lòng thử lại.");
        }
        return BACK_TO_TAB;
    }

    @PostMapping("/analyze/{id}")
    public String analyze(@PathVariable("id") Long id,
                          Authentication authentication,
                          RedirectAttributes ra) {
        User user = currentUserService.require(authentication);
        try {
            ExternalMedicalRecord record = externalMedicalRecordRepository.findById(id).orElse(null);
            // Dùng lại chính hàm gác quyền xem: chạy phân tích là đọc nội dung tệp, nên ai không
            // được xem thì cũng không được bấm nút này.
            String blocked = externalMedicalRecordService.whyCannotView(record, user);
            if (blocked != null) {
                ra.addFlashAttribute("errorMessage", blocked);
                return BACK_TO_TAB;
            }
            // Nút "Phân tích lại" bấm được bao nhiêu lần tuỳ ý, mỗi lần là một lượt gọi model
            // trả tiền — đây chính là đường lách hạn mức rẻ nhất nếu không kiểm.
            String overQuota = chatImageService.whyCannotAnalyzeImage(user);
            if (overQuota != null) {
                ra.addFlashAttribute("errorMessage", overQuota);
                return BACK_TO_TAB;
            }
            chatImageService.countOneAnalysis(user);

            externalMedicalRecordService.analyze(id);
            ra.addFlashAttribute("successMessage", "Đã phân tích lại hồ sơ.");
        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("errorMessage", "Không phân tích được hồ sơ. Vui lòng thử lại.");
        }
        return BACK_TO_TAB;
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable("id") Long id,
                         Authentication authentication,
                         RedirectAttributes ra) {
        User user = currentUserService.require(authentication);
        try {
            externalMedicalRecordService.delete(id, user);
            ra.addFlashAttribute("successMessage", "Đã xoá hồ sơ khỏi tài khoản của anh/chị.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("errorMessage", "Không xoá được hồ sơ. Vui lòng thử lại.");
        }
        return BACK_TO_TAB;
    }

    /**
     * Tải hồ sơ lên NGAY TRONG KHUNG CHAT rồi trả kết quả phân tích dưới dạng JSON.
     *
     * <p><b>Trả JSON chứ không redirect</b>, cùng lý do đã khiến
     * {@code DoctorMedicalRecordController.addAllergyDuringExam} phải làm vậy: một lần tải lại trang
     * sẽ xoá sạch đoạn hội thoại đang dở trong khung chat — thứ khách vừa gõ và cả ngữ cảnh mà trợ
     * lý đang giữ.
     *
     * <p><b>Cố ý nằm ở {@code /user/medical-document/**} chứ không phải {@code /api/chat/**}.</b>
     * Tiền tố này đã được khai {@code authenticated()} trong {@code SecurityConfig}, còn
     * {@code /api/chat/**} là {@code permitAll} nên mỗi endpoint đặt vào đó lại phải nhớ thêm một
     * dòng ở khối 0 — quên một lần là mở đường ghi dữ liệu cho người lạ. Và toàn bộ đường ghi hồ sơ
     * ngoại viện ở chung một lớp, không rải ra hai nơi.
     *
     * <p><b>Khách chưa đăng nhập KHÔNG tới được hàm này</b>: Spring Security chặn ở filter và trả
     * 302 sang {@code /login}. Nhánh 401 dưới đây chỉ bắt trường hợp còn lại — có phiên nhưng
     * không phân giải ra {@code User} nào. Khung chat vì vậy phải tự nhận ra redirect (fetch đi
     * theo 302 rồi trả trang đăng nhập kèm status 200), đúng cách trang {@code /checkout-qr} làm;
     * xét mỗi {@code res.ok} là báo nhầm thành lỗi mạng.
     */
    @PostMapping("/chat-upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> chatUpload(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {

        User user = currentUserService.find(authentication).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "NOT_LOGGED_IN"));
        }
        if (file == null || file.isEmpty()) {
            return reject("Anh/chị chọn tệp giúp em nhé.");
        }

        // Hạn mức TRƯỚC mọi thứ khác: một câu COUNT, và là cùng câu tiếng Việt mà màn hình in ra.
        String blocked = chatImageService.whyCannotAnalyzeImage(user);
        if (blocked != null) {
            return reject(blocked);
        }

        try {
            // PDF không bao giờ là ảnh triệu chứng — không ai chụp nốt ban thành PDF. Giữ nguyên
            // đường cũ (PDFBox bóc chữ), không đưa qua model thị giác.
            if (isPdf(file)) {
                // Nhánh này không qua model thị giác nhưng VẪN là một lượt gọi model trả tiền,
                // nên vẫn phải trừ hạn mức — bằng không vòng lặp gửi PDF chạy vô hạn.
                chatImageService.countOneAnalysis(user);
                return storeAsDocument(user, title, file, null);
            }

            // ẢNH: phân loại TRƯỚC, ghi đĩa SAU. Đây là ràng buộc, không phải tối ưu — nó là thứ
            // duy nhất làm cho lời hứa "ảnh triệu chứng không bao giờ được lưu" thành sự thật.
            // Bytes đi thẳng từ MultipartFile tới model, chưa từng chạm đĩa ở thời điểm này.
            ImageAnalysis analysis = chatImageService.analyze(
                    user, file.getBytes(), "chatimg-" + user.getId());

            if (analysis.isDocument()) {
                return storeAsDocument(user, title, file, analysis);
            }
            if (analysis.isSymptom()) {
                return symptomResult(analysis, sessionId);
            }

            Map<String, Object> body = new HashMap<>();
            body.put("ok", true);
            body.put("kind", ImageAnalysis.KIND_OTHER);
            body.put("message", analysis.getMessage());
            return ResponseEntity.ok(body);

        } catch (IllegalStateException e) {
            return reject(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "SERVER"));
        }
    }

    /** Giấy tờ: bây giờ mới ghi ra đĩa, và dùng luôn kết quả đã có thay vì gọi AI lần hai. */
    private ResponseEntity<Map<String, Object>> storeAsDocument(User user, String title,
                                                                MultipartFile file,
                                                                ImageAnalysis analysis) {
        ExternalMedicalRecord saved = externalMedicalRecordService.upload(
                user.getId(), resolveTitle(title, file), null, file);

        if (analysis == null) {
            // Nhánh PDF: chưa có phân tích nào, chạy đường cũ.
            externalMedicalRecordService.analyze(saved.getId());
        } else {
            externalMedicalRecordService.applyAnalysis(saved.getId(), analysis);
        }

        // Đọc lại sau khi ghi kết quả: `saved` là ảnh chụp TRƯỚC đó, trả thẳng nó về là khách luôn
        // nhìn thấy "PENDING" dù phân tích đã xong.
        ExternalMedicalRecord fresh = externalMedicalRecordRepository.findById(saved.getId())
                .orElse(saved);

        Map<String, Object> body = new HashMap<>();
        body.put("ok", true);
        body.put("kind", ImageAnalysis.KIND_DOCUMENT);
        body.put("document", externalMedicalRecordService.toCard(fresh));
        return ResponseEntity.ok(body);
    }

    /**
     * Ảnh triệu chứng: KHÔNG ghi đĩa, KHÔNG dòng DB nào. Chỉ trả kết quả về khung chat.
     *
     * <p>Ghi một ghi chú vào lịch sử hội thoại để lượt sau khách hỏi "nó có nguy hiểm không?" thì
     * model còn biết vừa nhìn thấy gì — ảnh đã biến mất, không có khối tiêm ngữ cảnh nào thay thế.
     */
    private ResponseEntity<Map<String, Object>> symptomResult(ImageAnalysis analysis, String sessionId) {
        StringBuilder note = new StringBuilder("(Hệ thống: bệnh nhân vừa gửi một ảnh chụp ");
        note.append(analysis.getBodyPart() == null || analysis.getBodyPart().isBlank()
                ? "vùng cơ thể đang có vấn đề" : analysis.getBodyPart());
        if (analysis.getFindings() != null && !analysis.getFindings().isEmpty()) {
            note.append(". Dấu hiệu nhìn thấy: ").append(String.join(", ", analysis.getFindings()));
        }
        note.append(". Ảnh đã được xoá, không lưu lại. Hãy dùng thông tin này để tư vấn tiếp, ")
            .append("nhưng TUYỆT ĐỐI không trình bày như chẩn đoán.)");
        aiService.appendAssistantNote(sessionId, note.toString());

        Map<String, Object> body = new HashMap<>();
        body.put("ok", true);
        body.put("kind", ImageAnalysis.KIND_SYMPTOM);
        body.put("symptom", chatImageService.toSymptomCard(analysis));
        return ResponseEntity.ok(body);
    }

    private static ResponseEntity<Map<String, Object>> reject(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", "REJECTED", "message", message));
    }

    private static boolean isPdf(MultipartFile file) {
        String name = org.springframework.util.StringUtils.getFilename(file.getOriginalFilename());
        return name != null && name.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
    }

    /**
     * Khung chat không có ô nhập tên hồ sơ — đặt tên từ chính tên tệp để khách còn nhận ra nó
     * trong danh sách ở trang hồ sơ. Tệp không có tên đọc được thì lấy ngày làm tên, chứ không để
     * rỗng: {@code title} là cột {@code NOT NULL}.
     */
    private static String resolveTitle(String provided, MultipartFile file) {
        if (provided != null && !provided.isBlank()) {
            return provided.trim();
        }
        String original = org.springframework.util.StringUtils.getFilename(file.getOriginalFilename());
        String base = org.springframework.util.StringUtils.stripFilenameExtension(
                original == null ? "" : original);
        if (base != null && !base.isBlank()) {
            return base.trim();
        }
        return "Hồ sơ tải lên ngày " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    /**
     * Trả về chính tệp bệnh nhân đã tải lên.
     *
     * <p><b>MỘT endpoint dùng chung cho cả bệnh nhân và bác sĩ</b>, dù đường dẫn mang tiền tố
     * {@code /user}. Tách thành hai endpoint theo tiền tố đối tượng thì nghe gọn hơn nhưng sẽ
     * thành HAI bản kiểm quyền có thể lệch nhau — đúng thứ mà luật {@code whyCannot…} sinh ra
     * để chặn. Trang bác sĩ trỏ thẳng vào đây.
     *
     * <p>Tệp nằm ở {@code app.private-dir}, thư mục KHÔNG được đăng ký với bất kỳ
     * {@code ResourceHandler} nào, nên đây là đường duy nhất đọc được nó — cùng khuôn
     * {@code AdminCandidateController.downloadCv}.
     */
    @GetMapping("/file/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable("id") Long id,
                                                 Authentication authentication) {
        User viewer = currentUserService.find(authentication).orElse(null);
        ExternalMedicalRecord record = externalMedicalRecordRepository.findById(id).orElse(null);
        if (externalMedicalRecordService.whyCannotView(record, viewer) != null) {
            // Cùng một mã cho "không phải của bạn" và "không tồn tại", để không dò được id nào có
            // thật — khuôn requireOwnedBooking của DoctorExamAiController.
            return ResponseEntity.notFound().build();
        }

        Path file = fileStorageService.resolveMedicalDocument(record.getStoredFileName());
        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        MediaType contentType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (record.getContentType() != null && !record.getContentType().isBlank()) {
                contentType = MediaType.parseMediaType(record.getContentType());
            }
        } catch (Exception ignored) {
            // Content-Type do trình duyệt gửi lên là dữ liệu client; hỏng thì rơi về octet-stream.
        }

        // inline chứ không attachment: ảnh và PDF xem ngay trong tab mới là thao tác bác sĩ cần
        // giữa ca khám, tải về rồi mở bằng ứng dụng khác thì chậm hơn hẳn.
        String downloadName = "hoso-" + id + "-" + file.getFileName();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + downloadName + "\"")
                .contentType(contentType)
                .body(new FileSystemResource(file));
    }
}
