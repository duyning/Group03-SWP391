package com.swp392.cinema2026.controller;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: FileUploadController.java
 * Chức năng: REST Controller chịu trách nhiệm xử lý tải lên (upload) và xóa video trailer phim.
 *            Hỗ trợ lưu trữ file video cục bộ trong thư mục tĩnh của máy chủ và trả về đường dẫn URL công khai.
 * Endpoints:
 *   - POST /api/upload/video: Upload file video lên server, tự động tạo tên file ngẫu nhiên để tránh trùng lặp.
 *   - DELETE /api/upload/video/{filename}: Xóa file video tương ứng khỏi ổ cứng server.
 * Người viết: TrienLX - HE182285
 * Ngày tạo: 2026-06-04
 */

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Controller xử lý việc upload file video từ máy người dùng lên máy chủ
@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*")
public class FileUploadController {

    // Đọc đường dẫn thư mục lưu video từ cấu hình application.yaml
    @Value("${app.upload.video-dir:src/main/resources/static/uploads/videos/}")
    private String videoUploadDir;

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
            // Tạo thư mục lưu trữ nếu chưa tồn tại
            Path uploadPath = Paths.get(videoUploadDir);
            Files.createDirectories(uploadPath);

            // Tạo tên file duy nhất bằng UUID để tránh trùng tên
            String suffix   = originalName.substring(originalName.lastIndexOf('.'));
            String uniqueName = UUID.randomUUID().toString() + suffix;
            Path targetPath   = uploadPath.resolve(uniqueName);

            // Sao chép dữ liệu file vào đường dẫn đích, ghi đè nếu tồn tại
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Đường dẫn URL công khai để trình duyệt có thể truy cập video
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
