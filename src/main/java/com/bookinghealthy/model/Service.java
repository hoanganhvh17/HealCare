package com.bookinghealthy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Bỏ import java.math.BigDecimal;

@Entity
@Table(name = "services")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // === THAY ĐỔI Ở ĐÂY ===
    @Column(nullable = false)
    private String price; // Đổi từ BigDecimal thành String
    // === KẾT THÚC THAY ĐỔI ===

    private String image;

    @Column(nullable = false)
    private String category;
}