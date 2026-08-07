package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Allergy;
import com.bookinghealthy.model.User;
import com.bookinghealthy.repository.AllergyRepository;
import com.bookinghealthy.repository.UserRepository;
import com.bookinghealthy.service.AllergyService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AllergyServiceImpl implements AllergyService {

    /** Khớp độ dài cột {@code severity}. */
    private static final int MAX_SEVERITY = 50;

    @Autowired private AllergyRepository allergyRepository;
    @Autowired private UserRepository userRepository;

    @Override
    public List<Allergy> findForUser(Long userId) {
        return allergyRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    @Transactional
    public Allergy add(Long userId, String allergen, String reaction, String severity, String source) {
        String cleanAllergen = allergen == null ? "" : allergen.trim();
        if (cleanAllergen.isEmpty()) {
            throw new IllegalStateException("Vui lòng nhập tác nhân gây dị ứng.");
        }
        if (allergyRepository.existsByUserIdAndAllergenIgnoreCase(userId, cleanAllergen)) {
            throw new IllegalStateException("\"" + cleanAllergen + "\" đã có trong hồ sơ dị ứng.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy bệnh nhân."));

        Allergy allergy = new Allergy();
        allergy.setUser(user);
        allergy.setAllergen(cleanAllergen);
        allergy.setReaction(reaction == null ? null : reaction.trim());
        allergy.setSeverity(truncate(severity));
        allergy.setSource(source);

        return allergyRepository.save(allergy);
    }

    @Override
    public String whyCannotDelete(Allergy allergy, User currentUser) {
        if (allergy == null || currentUser == null) {
            return "Không tìm thấy mục dị ứng.";
        }
        if (allergy.getUser() == null || !allergy.getUser().getId().equals(currentUser.getId())) {
            return "Đây không phải hồ sơ dị ứng của anh/chị.";
        }
        // Mục do bác sĩ ghi là dữ liệu lâm sàng: bệnh nhân xoá đi thì lần khám sau thẻ cảnh báo
        // đỏ mất luôn cảnh báo đó. Muốn bỏ thì phải nói với bác sĩ.
        if (Allergy.SOURCE_DOCTOR.equals(allergy.getSource())) {
            return "Mục này do bác sĩ ghi nhận, anh/chị không tự xoá được. "
                    + "Vui lòng trao đổi với bác sĩ trong lần khám tới.";
        }
        return null;
    }

    @Override
    @Transactional
    public void delete(Long allergyId, User currentUser) {
        Allergy allergy = allergyRepository.findById(allergyId).orElse(null);

        String blocked = whyCannotDelete(allergy, currentUser);
        if (blocked != null) {
            throw new IllegalStateException(blocked);
        }

        allergyRepository.delete(allergy);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.length() <= MAX_SEVERITY ? cleaned : cleaned.substring(0, MAX_SEVERITY);
    }
}
