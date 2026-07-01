package com.group3.cinema.controller.api;

/**
 * Dá»± Ã¡n: Cinema 2026 â€” SWP391 Group 03
 * File: FileUploadController.java
 * Chá»©c nÄƒng: REST Controller chá»‹u trÃ¡ch nhiá»‡m xá»­ lÃ½ táº£i lÃªn (upload) vÃ  xÃ³a video trailer phim.
 *            Há»— trá»£ lÆ°u trá»¯ file video cá»¥c bá»™ trong thÆ° má»¥c tÄ©nh cá»§a mÃ¡y chá»§ vÃ  tráº£ vá» Ä‘Æ°á»ng dáº«n URL cÃ´ng khai.
 * Endpoints:
 *   - POST /api/upload/video: Upload file video lÃªn server, tá»± Ä‘á»™ng táº¡o tÃªn file ngáº«u nhiÃªn Ä‘á»ƒ trÃ¡nh trÃ¹ng láº·p.
 *   - DELETE /api/upload/video/{filename}: XÃ³a file video tÆ°Æ¡ng á»©ng khá»i á»• cá»©ng server.
 * NgÆ°á»i viáº¿t: TrienLX - HE182285
 * NgÃ y táº¡o: 2026-06-04
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
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Controller xá»­ lÃ½ viá»‡c upload file video tá»« mÃ¡y ngÆ°á» i dÃ¹ng lÃªn mÃ¡y chá»§
@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*")
public class FileUploadController {

    // Ä á» c Ä‘Æ°á» ng dáº«n thÆ° má»¥c lÆ°u video tá»« cáº¥u hÃ¬nh application.yaml
    @Value("${app.upload.video-dir:uploads/videos/}")
    private String videoUploadDir;

    @Value("${app.upload.image-dir:uploads/images/}")
    private String imageUploadDir;

    // Endpoint: POST /api/upload/video
    // Nháº­n file video tá»« form multipart, lÆ°u vÃ o thÆ° má»¥c static vÃ  tráº£ vá» URL truy cáº­p
    @PostMapping("/video")
    public ResponseEntity<Map<String, String>> uploadVideo(
            @RequestParam("file") MultipartFile file) {

        Map<String, String> response = new HashMap<>();

        // Kiá»ƒm tra file khÃ´ng Ä‘Æ°á»£c rá»—ng
        if (file.isEmpty()) {
            response.put("error", "Vui lòng chọn file video.");
            return ResponseEntity.badRequest().body(response);
        }

        // Kiá»ƒm tra Ä‘á»‹nh dáº¡ng file â€” chá»‰ cháº¥p nháº­n cÃ¡c Ä‘á»‹nh dáº¡ng video phá»• biáº¿n
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
            // Táº¡o thÆ° má»¥c lÆ°u trá»¯ náº¿u chÆ°a tá»“n táº¡i
            Path uploadPath = Paths.get(videoUploadDir);
            Files.createDirectories(uploadPath);

            // Tạo tên file duy nhất bằng MD5 hash của file để phát hiện và tránh trùng lặp nội dung video trailer
            String suffix   = originalName.substring(originalName.lastIndexOf('.'));
            String hash     = calculateMD5(file.getBytes());
            String uniqueName = hash + suffix;
            Path targetPath   = uploadPath.resolve(uniqueName);

            // Sao chÃ©p dá»¯ liá»‡u file vÃ o Ä‘Æ°á»ng dáº«n Ä‘Ã­ch, ghi Ä‘Ã¨ náº¿u tá»“n táº¡i
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

            // ÄÆ°á»ng dáº«n URL cÃ´ng khai Ä‘á»ƒ trÃ¬nh duyá»‡t cÃ³ thá»ƒ truy cáº­p video
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

    @PostMapping("/image")
    public ResponseEntity<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        Map<String, String> response = new HashMap<>();

        if (file.isEmpty()) {
            response.put("error", "Vui lòng chọn file ảnh.");
            return ResponseEntity.badRequest().body(response);
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null) {
            response.put("error", "Tên file không hợp lệ.");
            return ResponseEntity.badRequest().body(response);
        }

        String ext = originalName.toLowerCase();
        if (!ext.endsWith(".jpg") && !ext.endsWith(".jpeg")
                && !ext.endsWith(".png") && !ext.endsWith(".webp")) {
            response.put("error", "Định dạng ảnh không được hỗ trợ. Vui lòng dùng JPG, PNG hoặc WebP.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            Path uploadPath = Paths.get(imageUploadDir);
            Files.createDirectories(uploadPath);

            String suffix = originalName.substring(originalName.lastIndexOf('.'));
            String uniqueName = UUID.randomUUID().toString() + suffix;
            Path targetPath = uploadPath.resolve(uniqueName);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

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
    // XÃ³a file video khá»i mÃ¡y chá»§ khi khÃ´ng cÃ²n sá»­ dá»¥ng
    @DeleteMapping("/video/{filename}")
    public ResponseEntity<Map<String, String>> deleteVideo(@PathVariable("filename") String filename) {
        Map<String, String> response = new HashMap<>();
        try {
            // NgÄƒn cháº·n path traversal attack báº±ng cÃ¡ch chá»‰ láº¥y tÃªn file thuáº§n tÃºy
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

    private String calculateMD5(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
