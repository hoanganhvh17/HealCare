package com.bookinghealthy.controller.api;

import com.bookinghealthy.dto.DailyBookingStatsDTO;
import com.bookinghealthy.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Date;
// import java.math.BigInteger; // <-- KHÔNG CẦN NỮA
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    @Autowired
    private BookingRepository bookingRepository;

    @GetMapping("/booking-stats-7days")
    public List<DailyBookingStatsDTO> getBookingStats() {
        List<Object[]> results = bookingRepository.getBookingStatsForLast7Days();
        List<DailyBookingStatsDTO> stats = new ArrayList<>();

        for (Object[] result : results) {
            Date date = (Date) result[0];

            // === SỬA LỖI Ở ĐÂY ===
            // MySQL trả về Long (hoặc BigInteger tùy phiên bản),
            // chúng ta ép kiểu (cast) về Number (lớp cha) rồi lấy longValue() cho an toàn.
            Number count = (Number) result[1];
            // === KẾT THÚC SỬA LỖI ===

            stats.add(new DailyBookingStatsDTO(date.toString(), count.longValue()));
        }
        return stats;
    }
}