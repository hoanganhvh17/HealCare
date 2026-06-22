package com.bookinghealthy.service.impl;

import com.bookinghealthy.model.Service; // <-- GIỮ NGUYÊN import này
import com.bookinghealthy.repository.ServiceRepository;
import com.bookinghealthy.service.ServiceService;
import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.stereotype.Service; // <-- XÓA DÒNG NÀY

import java.util.List;
import java.util.Optional;

// SỬA DÒNG NÀY: Dùng tên đầy đủ của annotation
@org.springframework.stereotype.Service
public class ServiceServiceImpl implements ServiceService {

    @Autowired
    private ServiceRepository serviceRepository;

    @Override
    public List<Service> findAll() { // <-- Giờ Java sẽ hiểu đây là model.Service
        return serviceRepository.findAll();
    }

    @Override
    public List<Service> findByCategory(String category) { // <-- Giờ Java sẽ hiểu đây là model.Service
        return serviceRepository.findByCategory(category);
    }
    // === THÊM PHƯƠNG THỨC MỚI NÀY ===
    @Override
    public Optional<Service> findById(Long id) {
        return serviceRepository.findById(id);
    }
    // === THÊM 2 PHƯƠNG THỨC MỚI NÀY ===
    @Override
    public Service save(Service service) {
        return serviceRepository.save(service);
    }

    @Override
    public void deleteById(Long id) {
        serviceRepository.deleteById(id);
    }
}