package com.bookinghealthy.service;

import com.bookinghealthy.config.VNPayConfig;
import com.bookinghealthy.config.VnPayProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter VNP_DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private static final int EXPIRE_MINUTES = 3;

    private final VnPayProperties vnpay;

    public PaymentService(VnPayProperties vnpay) {
        this.vnpay = vnpay;
    }

    public record VnPayPayment(String paymentUrl, String txnRef) {
    }

    public VnPayPayment createVnPayPayment(HttpServletRequest request, long amount,
                                           String orderInfo, String returnUrl)
            throws UnsupportedEncodingException {

        String vnp_TxnRef = VNPayConfig.getRandomNumber(8);

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnpay.getTmnCode());
        vnp_Params.put("vnp_Amount", String.valueOf(amount * 100)); // VNPay tính đơn vị là 'xu'
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", vnp_TxnRef);
        vnp_Params.put("vnp_OrderInfo", orderInfo);
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", returnUrl);
        vnp_Params.put("vnp_IpAddr", VNPayConfig.getIpAddress(request));

        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        vnp_Params.put("vnp_CreateDate", now.format(VNP_DATE_FMT));
        vnp_Params.put("vnp_ExpireDate", now.plusMinutes(EXPIRE_MINUTES).format(VNP_DATE_FMT));

        String hashData = buildHashData(vnp_Params);
        String secureHash = VNPayConfig.hmacSHA512(vnpay.getHashSecret(), hashData);

        String queryUrl = buildQuery(vnp_Params) + "&vnp_SecureHash=" + secureHash;
        return new VnPayPayment(vnpay.getPayUrl() + "?" + queryUrl, vnp_TxnRef);
    }

    public boolean isValidSignature(Map<String, String[]> rawParams) {
        if (rawParams == null) {
            return false;
        }
        String received = firstValue(rawParams.get("vnp_SecureHash"));
        if (received == null || received.isBlank()) {
            return false;
        }
        Map<String, String> fields = new HashMap<>();
        rawParams.forEach((name, values) -> {
            if (name.startsWith("vnp_")
                    && !"vnp_SecureHash".equals(name)
                    && !"vnp_SecureHashType".equals(name)) {
                String v = firstValue(values);
                if (v != null && !v.isEmpty()) {
                    fields.put(name, v);
                }
            }
        });
        if (fields.isEmpty()) {
            return false;
        }
        String expected;
        try {
            expected = VNPayConfig.hmacSHA512(vnpay.getHashSecret(), buildHashData(fields));
        } catch (UnsupportedEncodingException e) {
            return false;
        }
        return expected.equalsIgnoreCase(received);
    }

    public boolean isOurMerchant(String tmnCode) {
        return vnpay.getTmnCode() != null && vnpay.getTmnCode().equals(tmnCode);
    }

    private String buildHashData(Map<String, String> params) throws UnsupportedEncodingException {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        for (int i = 0; i < fieldNames.size(); i++) {
            String name = fieldNames.get(i);
            String value = params.get(name);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (hashData.length() > 0) {
                hashData.append('&');
            }
            hashData.append(name).append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.US_ASCII.toString()));
        }
        return hashData.toString();
    }

    private String buildQuery(Map<String, String> params) throws UnsupportedEncodingException {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder query = new StringBuilder();
        for (String name : fieldNames) {
            String value = params.get(name);
            if (value == null || value.isEmpty()) {
                continue;
            }
            if (query.length() > 0) {
                query.append('&');
            }
            query.append(URLEncoder.encode(name, StandardCharsets.US_ASCII.toString()))
                    .append('=')
                    .append(URLEncoder.encode(value, StandardCharsets.US_ASCII.toString()));
        }
        return query.toString();
    }

    private static String firstValue(String[] values) {
        return (values == null || values.length == 0) ? null : values[0];
    }
}
