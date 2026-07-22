/**
 * Lớp khởi tạo chính của ứng dụng Quản lý đặt vé xem phim Rạp (Group03 - SWP391 Summer 2026).
 * 
 * Bật các tính năng:
 * - @SpringBootApplication: Tự động cấu hình Spring Boot, quét các thành phần (Component Scan).
 * - @EnableAsync: Hỗ trợ chạy các tác vụ bất đồng bộ (ví dụ: gửi email xác nhận đặt vé).
 * - @EnableScheduling: Hỗ trợ lập lịch tác vụ tự động (ví dụ: tự động hủy đơn đặt vé hết hạn - BookingExpirationJob).
 * 
 * Ngày cập nhật: 04/06/2026
 * Khởi tạo bởi: NinhDD - HE186113
 */
package com.group3.cinema;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class Group03Swp391Summer26Application {

    /**
     * Phương thức khởi chạy ứng dụng Spring Boot.
     * 
     * Hàm này gọi đến SpringApplication.run() để khởi động Spring Context,
     * khởi tạo các Bean, kết nối CSDL SQL Server và bắt đầu nhận request trên cổng cấu hình (8081).
     * 
     * @param args Các tham số truyền vào từ dòng lệnh khi chạy ứng dụng.
     */
    public static void main(String[] args) {
        SpringApplication.run(Group03Swp391Summer26Application.class, args);
    }
}

