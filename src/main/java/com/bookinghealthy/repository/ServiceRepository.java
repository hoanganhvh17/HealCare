package com.bookinghealthy.repository;

import com.bookinghealthy.model.Service;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List; // Thêm import này

@Repository
public interface ServiceRepository extends JpaRepository<Service, Long> {

    // === THÊM PHƯƠNG THỨC MỚI NÀY ===
    List<Service> findByCategory(String category);
}