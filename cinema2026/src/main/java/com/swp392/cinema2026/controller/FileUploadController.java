package com.swp392.cinema2026.controller;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: FileUploadController.java
 * Chức năng: REST Controller chịu trách nhiệm xử lý tải lên (upload) và xóa file video trailer cũng như ảnh poster.
 *            Hỗ trợ lưu trữ cục bộ và trả về đường dẫn URL công khai.
 * Endpoints:
 *   - POST /api/upload/video: Upload file video trailer.
 *   - POST /api/upload/image: Upload file ảnh poster.
 *   - DELETE /api/upload/video/{filename}: Xóa file video tương ứng.
 * Người viết: TrienLX - HE182285
 * Người sửa: TrienLX
 * Ngày sửa: 2026-06-12
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*")
public class FileUploadController {

    // Đọc đường dẫn thư mục lưu video từ cấu hình application.yaml
    @Value("${app.upload.video-dir:src/main/resources/static/uploads/videos/}")
    private String videoUploadDir;

    // Đọc đường dẫn thư mục lưu ảnh từ cấu hình application.yaml
    @Value("${app.upload.image-dir:src/main/resources/static/uploads/images/}")
    private String imageUploadDir;

    // Endpoint: POST /api/upload/video
    // Nhận file video từ form multipart, lưu vào thư mục static và trả về URL truy cập
    @PostMapping("/video")
    public ResponseEntity<Map<String, String>> uploadVideo(
            @RequestParam("file") MultipartFile file) {

        Map<String, String> response = new HashMap<>();

        // Kiểm tra file không được rỗng
        if (file.isEmpty()) {
            response.put("error", "Vui lòng chọn file video.");
            return ResponseEntity.badRequest().body(response);
        }

        // Kiểm tra định dạng file — chỉ chấp nhận các định dạng video phổ biến
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            response.put("error", "Tên file không hợp lệ.");
            return ResponseEntity.badRequest().body(response);
        }

        String ext = originalName.toLowerCase();
        if (!ext.endsWith(".mp4") && !ext.endsWith(".webm") &&
            !ext.endsWith(".mkv") && !ext.endsWith(".avi") && !ext.endsWith(".mov")) {
            response.put("error", "Định dạng video không được hỗ trợ. Vui lòng dùng MP4, WebM, MKV, AVI hoặc MOV.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Tạo thư mục lưu trữ video nếu chưa tồn tại trên ổ đĩa server
            Path uploadPath = Paths.get(videoUploadDir);
            Files.createDirectories(uploadPath);

            // Lấy đuôi mở rộng của file video (.mp4, .webm, ...)
            String suffix = originalName.substring(originalName.lastIndexOf('.'));
            
            // Tạo file tạm thời với tên UUID ngẫu nhiên để nhận luồng ghi dữ liệu lần đầu
            String tempName = java.util.UUID.randomUUID().toString() + suffix;
            Path tempPath = uploadPath.resolve(tempName);

            // Sao chép luồng dữ liệu file tải lên vào file tạm thời
            Files.copy(file.getInputStream(), tempPath, StandardCopyOption.REPLACE_EXISTING);

            // Tính mã băm MD5 từ file tạm thời đã ghi xong trên ổ cứng.
            String md5;
            try (java.io.InputStream is = Files.newInputStream(tempPath)) {
                md5 = DigestUtils.md5DigestAsHex(is);
            }

            // Tên file mới chính thức = mã MD5 + đuôi mở rộng
            String uniqueName = md5 + suffix;
            Path targetPath = uploadPath.resolve(uniqueName);

            // Di chuyển file tạm thời sang file MD5 chính thức, ghi đè nếu đã tồn tại
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Đường dẫn URL công khai để trình duyệt có thể truy cập video từ client
            String publicUrl = "/uploads/videos/" + uniqueName;

            response.put("url",          publicUrl);
            response.put("originalName", originalName);
            response.put("size",         String.valueOf(file.getSize()));
            response.put("message",      "Upload thành công!");

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            response.put("error", "Lỗi khi lưu file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // Endpoint: POST /api/upload/image
    // Nhận file ảnh từ form multipart, lưu vào thư mục static và trả về URL truy cập
    @PostMapping("/image")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file) {
        
        Map<String, String> response = new HashMap<>();

        // Kiểm tra file ảnh tải lên không được rỗng
        if (file.isEmpty()) {
            response.put("error", "Vui lòng chọn file ảnh.");
            return ResponseEntity.badRequest().body(response);
        }

        // Lấy tên file gốc từ client
        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            response.put("error", "Tên file không hợp lệ.");
            return ResponseEntity.badRequest().body(response);
        }

        // Kiểm tra định dạng ảnh được hỗ trợ (JPG, PNG, WebP)
        String ext = originalName.toLowerCase();
        if (!ext.endsWith(".jpg") && !ext.endsWith(".jpeg")
                && !ext.endsWith(".png") && !ext.endsWith(".webp")) {
            response.put("error", "Định dạng ảnh không được hỗ trợ. Vui lòng sử dụng JPG, JPEG, PNG hoặc WebP.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            // Tạo thư mục lưu trữ ảnh poster nếu chưa tồn tại
            Path uploadPath = Paths.get(imageUploadDir);
            Files.createDirectories(uploadPath);

            // Lấy đuôi mở rộng của file ảnh (.jpg, .png, ...)
            String suffix = originalName.substring(originalName.lastIndexOf('.'));

            // Tạo file tạm thời với tên UUID ngẫu nhiên để nhận luồng ghi dữ liệu lần đầu
            String tempName = java.util.UUID.randomUUID().toString() + suffix;
            Path tempPath = uploadPath.resolve(tempName);

            // Sao chép file tải lên vào file tạm thời
            Files.copy(file.getInputStream(), tempPath, StandardCopyOption.REPLACE_EXISTING);

            // Tính mã băm MD5 của file tạm thời đã ghi trên đĩa
            String md5;
            try (java.io.InputStream is = Files.newInputStream(tempPath)) {
                md5 = DigestUtils.md5DigestAsHex(is);
            }

            // Tên file chính thức = mã MD5 + đuôi mở rộng
            String uniqueName = md5 + suffix;
            Path targetPath = uploadPath.resolve(uniqueName);

            // Di chuyển file tạm thời thành file MD5 chính thức
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Đường dẫn URL công khai của ảnh poster
            response.put("url", "/uploads/images/" + uniqueName);
            response.put("originalName", originalName);
            response.put("size", String.valueOf(file.getSize()));
            response.put("message", "Upload ảnh thành công!");
            
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            response.put("error", "Lỗi khi lưu file ảnh: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    // Endpoint: DELETE /api/upload/video/{filename}
    // Xóa file video khỏi máy chủ khi không còn sử dụng
    @DeleteMapping("/video/{filename}")
    public ResponseEntity<Map<String, String>> deleteVideo(@PathVariable String filename) {
        Map<String, String> response = new HashMap<>();
        try {
            // Ngăn chặn path traversal attack bằng cách chỉ lấy tên file thuần túy
            String safeName = Paths.get(filename).getFileName().toString();
            Path target = Paths.get(videoUploadDir).resolve(safeName);

            if (Files.exists(target)) {
                Files.delete(target);
                response.put("message", "Đã xóa file: " + safeName);
            } else {
                response.put("message", "File không tồn tại.");
            }
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            response.put("error", "Lỗi khi xóa file: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }
}
