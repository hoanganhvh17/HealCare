package com.bookinghealthy.service;

import com.bookinghealthy.model.Service;
import java.util.List;
import java.util.Optional;

public interface ServiceService {
    List<Service> findAll();
    List<Service> findByCategory(String category);
    Optional<Service> findById(Long id); // <-- THÊM DÒNG NÀY
    // (Chúng ta sẽ thêm save, delete, update khi làm Admin)
    // === THÊM 2 HÀM MỚI ===
    Service save(Service service);
    void deleteById(Long id);
}