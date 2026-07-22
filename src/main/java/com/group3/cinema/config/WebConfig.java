/**
 * Cấu hình Web MVC chính của ứng dụng (Spring Web Config).
 * 
 * Đảm nhận các vai trò:
 * 1. Đăng ký Resource Handlers phục vụ việc xem các tệp tin tĩnh upload lên (ảnh, video banner, poster phim).
 * 2. Đăng ký các AuthInterceptor để phân quyền truy cập URL theo vai trò (Guest, Customer, Manager, Admin).
 */
package com.group3.cinema.config;

import com.group3.cinema.entity.Role;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * Cấu hình đường dẫn phục vụ tệp tĩnh tải lên (Uploads).
     * 
     * Hàm này gọi đến `registry.addResourceHandler()` để map các request có đường dẫn `/uploads/**`
     * tới thư mục vật lý `uploads/` trong dự án.
     * 
     * @param registry ResourceHandlerRegistry đối tượng quản lý tài nguyên tĩnh.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }

    /**
     * Đăng ký các bộ chặn (Interceptors) để kiểm soát quyền truy cập URL.
     * 
     * Hàm này gọi `registry.addInterceptor()` với các thể hiện của `AuthInterceptor`:
     * - Interceptor 1: Yêu cầu đăng nhập chung cho toàn bộ ứng dụng (`/**`), loại trừ các trang public (xem phim, đăng nhập, static assets, callback thanh toán).
     * - Interceptor 2: Yêu cầu vai trò ADMIN hoặc MANAGER cho các trang/API quản lý (quản lý phim, lịch chiếu, vé, quầy vé).
     * - Interceptor 3: Yêu cầu riêng vai trò ADMIN đối với trang tạo tài khoản Manager, danh sách tài khoản.
     * - Interceptor 4: Phân định các tính năng giới hạn cho MANAGER.
     * 
     * @param registry InterceptorRegistry đối tượng quản lý Interceptors.
     */
    @org.springframework.beans.factory.annotation.Autowired
    private SeatHoldReleaseInterceptor seatHoldReleaseInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/",
                        "/home",
                        "/login",
                        "/register",
                        "/register/otp",
                        "/register/resend-otp",
                        "/forgot-password",
                        "/forgot-password/**",
                        "/reset-password",
                        "/reset-password/**",
                        "/access-denied",
                        "/movies",
                        "/movies/**",
                        "/posts",
                        "/posts/**",
                        "/news",
                        "/news/**",
                        "/promotions",
                        "/promotions/**",
                        "/uu-dai",
                        "/uu-dai/**",
                        "/about",
                        "/gioi-thieu",
                        "/contact",
                        "/lien-he",
                        "/general-terms",
                        "/dieu-khoan-chung",
                        "/transaction-terms",
                        "/dieu-khoan-giao-dich",
                        "/payment-policy",
                        "/chinh-sach-thanh-toan",
                        "/privacy-policy",
                        "/chinh-sach-bao-mat",
                        "/faq",
                        "/cau-hoi-thuong-gap",
                        "/partners",
                        "/danh-cho-doi-tac",
                        "/cinema-rules",
                        "/quy-dinh-tai-rap",
                        "/api/promotions/active",
                        "/search",
                        "/payment/vnpay/**",
                        "/payment/momo/**",
                        "/payment/payos/**",
                        "/payment/result",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/fonts/**",
                        "/static/**",
                        "/uploads/**",
                        "/error"
                );

        registry.addInterceptor(new AuthInterceptor(Role.ADMIN, Role.MANAGER))
                .addPathPatterns(
                        "/admin/**",
                        "/manage_movies.html",
                        "/manage_showtime.html",
                        "/manage_ticket.html",
                        "/api/movies/**",
                        "/api/showtimes/**",
                        "/api/tickets/**",
                        "/api/counter-sales/**",
                        "/api/rooms/**",
                        "/api/upload/**",
                        "/api/suggestions/persons/**"
                );

        registry.addInterceptor(new AuthInterceptor(
                        "/admin/dashboard",
                        "Chức năng này chỉ dành cho Admin.",
                        Role.ADMIN
                ))
                .addPathPatterns(
                        "/admin/create-manager",
                        "/admin/accounts",
                        "/admin/accounts/**"
                );

        registry.addInterceptor(new AuthInterceptor(
                        "/admin/dashboard",
                        "Chức năng này chỉ dành cho Manager.",
                        Role.MANAGER
                ))
                .addPathPatterns(
                        "/admin/**",
                        "/manage_movies.html",
                        "/manage_showtime.html",
                        "/manage_ticket.html",
                        "/api/movies/**",
                        "/api/showtimes/**",
                        "/api/tickets/**",
                        "/api/counter-sales/**",
                        "/api/rooms/**",
                        "/api/upload/**",
                        "/api/suggestions/persons/**"
                )
                .excludePathPatterns(
                        "/admin/dashboard",
                        "/admin/reports",
                        "/admin/reports/**",
                        "/admin/create-manager",
                        "/admin/accounts",
                        "/admin/accounts/**"
                );
    }
}

