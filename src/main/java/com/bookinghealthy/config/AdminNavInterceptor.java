package com.bookinghealthy.config;

import com.bookinghealthy.model.CandidateStatus;
import com.bookinghealthy.repository.CandidateRepository;
import com.bookinghealthy.repository.PostRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bơm trạng thái điều hướng cho mọi trang {@code /admin/**}: mục sidebar nào đang mở
 * ({@code activePage}) và các con số huy hiệu.
 *
 * <p><b>Vì sao là interceptor chứ không phải sửa 9 controller.</b> Sidebar admin trước đây hardcode
 * trạng thái active — mục Dashboard vĩnh viễn sáng, 11 mục còn lại vĩnh viễn tối, đi đâu cũng không
 * đổi. Cách các vai trò khác làm là {@code model.addAttribute("activePage", ...)} rải trong từng
 * controller; áp cách đó ở đây là 9 tệp và ~29 điểm vào, và mỗi trang thêm sau này lại là một chỗ
 * nữa để quên. Một bảng tiền tố URL nằm cạnh chính cái sidebar nó phục vụ thì không quên được.
 *
 * <p><b>Vì sao là {@code postHandle} chứ không phải {@code @ControllerAdvice}.</b> Hai lý do, mỗi
 * lý do vá một cách hỏng khác nhau:
 * <ul>
 *   <li>{@code AdminCandidateController.downloadCv} trả {@code ResponseEntity<Resource>}. Một
 *       phương thức {@code @ModelAttribute} sẽ chạy hai câu {@code COUNT} cho <b>mỗi lượt tải CV</b>
 *       rồi vứt kết quả đi. Chốt chặn {@code mv == null} ở đây bỏ qua cả trường hợp đó lẫn 17
 *       phương thức trả {@code redirect:}.</li>
 *   <li>{@code @ModelAttribute} chạy <b>trước</b> handler, nên một truy vấn hỏng ở đó là HTTP 500
 *       cho <b>mọi</b> URL admin — dự án không có global exception handler nào đỡ.
 *       {@code postHandle} chạy sau khi handler đã xong, nên tệ nhất là mất cái huy hiệu.</li>
 * </ul>
 *
 * <p><b>Tiền tố {@code nav} trên mọi thuộc tính là bắt buộc.</b> {@code AbstractView} trộn FlashMap
 * vào model <i>trước</i> model của handler, nên một thuộc tính trùng tên sẽ <b>nuốt</b> thông báo.
 * Mà {@code RedirectAttributes} là kênh báo lỗi duy nhất của ứng dụng này — đặt trùng
 * {@code errorMessage} hay {@code successMessage} là mất sạch thông báo trên mọi redirect admin,
 * không một dòng log.
 */
@Component
public class AdminNavInterceptor implements HandlerInterceptor {

    /**
     * Tiền tố URL {@literal ->} khoá sidebar. Duyệt theo thứ tự, khớp đầu tiên thắng, nên tiền tố
     * DÀI hơn phải đứng trước tiền tố ngắn hơn bao nó.
     */
    private static final Map<String, String> PAGE_KEYS = new LinkedHashMap<>();
    static {
        PAGE_KEYS.put("/admin/dashboard", "dashboard");
        PAGE_KEYS.put("/admin/manage-booking", "bookings");
        PAGE_KEYS.put("/admin/medical-records", "records");
        PAGE_KEYS.put("/admin/departments", "departments");
        PAGE_KEYS.put("/admin/manage-doctor", "doctors");
        PAGE_KEYS.put("/admin/manage-service", "services");
        PAGE_KEYS.put("/admin/manage-news", "news");
        PAGE_KEYS.put("/admin/recruitment", "jobs");
        PAGE_KEYS.put("/admin/candidates", "candidates");
        PAGE_KEYS.put("/admin/manage-user", "users");
    }

    private final PostRepository postRepository;
    private final CandidateRepository candidateRepository;

    public AdminNavInterceptor(PostRepository postRepository, CandidateRepository candidateRepository) {
        this.postRepository = postRepository;
        this.candidateRepository = candidateRepository;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler, ModelAndView mv) {
        // Không có view thì không có sidebar để tô: tải CV, tải PDF, mọi redirect.
        if (mv == null || mv.getViewName() == null || mv.getViewName().startsWith("redirect:")) {
            return;
        }

        String uri = request.getRequestURI();
        String ctx = request.getContextPath();
        if (ctx != null && !ctx.isEmpty() && uri.startsWith(ctx)) {
            uri = uri.substring(ctx.length());
        }
        for (Map.Entry<String, String> e : PAGE_KEYS.entrySet()) {
            if (uri.startsWith(e.getKey())) {
                mv.addObject("activePage", e.getValue());
                break;
            }
        }

        // Huy hiệu điều hướng KHÔNG được phép làm sập trang: mất một con số nhỏ rẻ hơn nhiều so với
        // một trang trắng. Nuốt lỗi vào log theo đúng khuôn xử lý lỗi sẵn có của dự án.
        try {
            long drafts = postRepository.countByStatus("DRAFT");
            long candidates = candidateRepository.countByStatus(CandidateStatus.PENDING);
            mv.addObject("navDraftPosts", drafts);
            mv.addObject("navPendingCandidates", candidates);
            mv.addObject("navContentTotal", drafts + candidates);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
