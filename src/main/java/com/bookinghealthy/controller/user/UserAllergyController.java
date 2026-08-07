package com.bookinghealthy.controller.user;

import com.bookinghealthy.model.Allergy;
import com.bookinghealthy.model.User;
import com.bookinghealthy.service.AllergyService;
import com.bookinghealthy.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Bệnh nhân tự khai dị ứng, trong tab "Hồ sơ y tế" của trang hồ sơ cá nhân.
 *
 * Tách khỏi {@code ProfileController} (đã ~240 dòng, lo hồ sơ + lịch sử khám + đổi mật khẩu)
 * theo đúng cách repo tách {@code controller/head} thành hai lớp.
 *
 * Trước khi có màn hình này, bảng {@code allergies} không có bất kỳ đường ghi nào — nên thẻ
 * "CẢNH BÁO DỊ ỨNG" ở form khám và trợ lý AI đối chiếu đơn thuốc đều chạy trên một kho rỗng.
 */
@Controller
@RequestMapping("/user/allergy")
public class UserAllergyController {

    /** Quay lại đúng tab "Hồ sơ y tế"; JS sẵn có trong profile.html mở tab theo hash. */
    private static final String BACK_TO_TAB = "redirect:/user/profile#medical-records";

    @Autowired private CurrentUserService currentUserService;
    @Autowired private AllergyService allergyService;

    @PostMapping("/add")
    public String addAllergy(@RequestParam("allergen") String allergen,
                             @RequestParam(value = "reaction", required = false) String reaction,
                             @RequestParam(value = "severity", required = false) String severity,
                             Authentication authentication,
                             RedirectAttributes ra) {
        User user = currentUserService.require(authentication);
        try {
            allergyService.add(user.getId(), allergen, reaction, severity, Allergy.SOURCE_PATIENT);
            ra.addFlashAttribute("successMessage", "Đã thêm vào hồ sơ dị ứng của anh/chị.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("errorMessage", "Không lưu được mục dị ứng. Vui lòng thử lại.");
        }
        return BACK_TO_TAB;
    }

    @PostMapping("/delete/{id}")
    public String deleteAllergy(@PathVariable("id") Long id,
                                Authentication authentication,
                                RedirectAttributes ra) {
        User user = currentUserService.require(authentication);
        try {
            // Luật nằm trong AllergyService.whyCannotDelete — cũng chính là luật template dùng
            // để ẩn nút, nên POST tự chế bị từ chối đúng lý do đang hiển thị trên màn hình.
            allergyService.delete(id, user);
            ra.addFlashAttribute("successMessage", "Đã xoá khỏi hồ sơ dị ứng.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ra.addFlashAttribute("errorMessage", "Không xoá được mục dị ứng. Vui lòng thử lại.");
        }
        return BACK_TO_TAB;
    }
}
