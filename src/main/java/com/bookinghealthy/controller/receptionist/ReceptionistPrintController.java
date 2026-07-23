package com.bookinghealthy.controller.receptionist;

import com.bookinghealthy.service.PdfExportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Xuất phiếu thu / đơn thuốc dạng PDF để lễ tân in ra giấy đưa bệnh nhân.
 */
@Controller
@RequestMapping("/receptionist/print")
public class ReceptionistPrintController {

    @Autowired
    private PdfExportService pdfExportService;

    @GetMapping("/receipt/{bookingId}")
    public ResponseEntity<byte[]> receipt(@PathVariable Long bookingId) {
        try {
            return pdfResponse(pdfExportService.buildReceipt(bookingId), "phieu-thu-" + bookingId + ".pdf");
        } catch (Exception e) {
            return redirectWithError("Không in được phiếu thu: " + e.getMessage());
        }
    }

    @GetMapping("/prescription/{bookingId}")
    public ResponseEntity<byte[]> prescription(@PathVariable Long bookingId) {
        try {
            return pdfResponse(pdfExportService.buildPrescription(bookingId), "don-thuoc-" + bookingId + ".pdf");
        } catch (Exception e) {
            return redirectWithError("Không in được đơn thuốc: " + e.getMessage());
        }
    }

    private ResponseEntity<byte[]> pdfResponse(byte[] pdf, String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        // inline: mở sẵn trong tab để lễ tân bấm in ngay, vẫn tải về được nếu cần
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"");
        headers.setContentLength(pdf.length);
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    /**
     * Không dùng RedirectAttributes ở đây: kiểu trả về là ResponseEntity nên Spring
     * không đánh dấu đây là kịch bản redirect và flash attribute sẽ bị bỏ qua.
     * Truyền thông báo qua query param để trang danh sách hiển thị lại.
     */
    private ResponseEntity<byte[]> redirectWithError(String message) {
        URI target = UriComponentsBuilder.fromPath("/receptionist/bookings")
                .queryParam("printError", message)
                .build()
                .encode()
                .toUri();

        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }
}
