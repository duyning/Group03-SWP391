package com.swp392.cinema2026;

/**
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: Cinema2026Application.java
 * Chức năng: Entry point (điểm khởi chạy chính) của ứng dụng Spring Boot. Chịu trách nhiệm khởi động
 *            toàn bộ cấu hình Spring Boot và chạy web server cho hệ thống Cinema 2026.
 * Người viết: TrienLX - HE182285
 * Ngày tạo: 2026-06-04
 */

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class Cinema2026Application {

	public static void main(String[] args) {
		SpringApplication.run(Cinema2026Application.class, args);
	}

}
