//package com.bookinghealthy.service;
//
//import org.imgscalr.Scalr;
//import org.springframework.stereotype.Service;
//import org.springframework.web.multipart.MultipartFile;
//
//import javax.imageio.ImageIO;
//import java.awt.image.BufferedImage;
//import java.io.File;
//import java.io.IOException;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//
//@Service
//public class ImageService {
//
//    // === THAY ĐỔI QUAN TRỌNG: Lưu ra thư mục gốc của dự án ===
//    private final String UPLOAD_DIR = "uploads/";
//
//    public String saveAvatar(MultipartFile file) throws IOException {
//        // 1. Tạo thư mục "uploads" ở gốc dự án nếu chưa có
//        File dir = new File(UPLOAD_DIR);
//        if (!dir.exists()) dir.mkdirs();
//
//        // 2. Tạo tên file duy nhất
//        String fileName = System.currentTimeMillis() + ".jpg";
//        File targetFile = new File(dir.getAbsolutePath() + File.separator + fileName);
//
//        // 3. Đọc ảnh gốc
//        BufferedImage originalImage = ImageIO.read(file.getInputStream());
//        if (originalImage == null) {
//            throw new IOException("File không hợp lệ");
//        }
//
//        // 4. Cắt và Resize (Logic giữ nguyên)
//        int width = originalImage.getWidth();
//        int height = originalImage.getHeight();
//        int minSize = Math.min(width, height);
//
//        BufferedImage croppedImage = Scalr.crop(originalImage,
//                (width - minSize) / 2, (height - minSize) / 2,
//                minSize, minSize);
//
//        BufferedImage resizedImage = Scalr.resize(croppedImage, Scalr.Method.QUALITY, Scalr.Mode.FIT_EXACT, 300, 300);
//
//        // 5. Lưu file
//        ImageIO.write(resizedImage, "jpg", targetFile);
//
//        return fileName;
//    }
//}