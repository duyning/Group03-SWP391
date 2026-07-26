package com.group3.cinema.config;

import com.group3.cinema.entity.Role;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Interceptor chung yeu cau dang nhap cho moi URL khong nam trong exclude.
        // Vi /profile va /my-tickets khong bi exclude, hai nhom URL nay duoc bao ve.
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
                        // Controller ResetPassword van tu kiem tra loggedInUser trong session.
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

        // Bao ve toan bo khu vuc quan tri cho ADMIN hoac MANAGER.
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

        // Lop quyen hep hon: chi ADMIN duoc Manage Account va Manage Manager.
        registry.addInterceptor(new AuthInterceptor(
                        "/admin/dashboard",
                        "Chức năng này chỉ dành cho Admin.",
                        Role.ADMIN
                ))
                .addPathPatterns(
                        // URL GET/POST tao tai khoan MANAGER.
                        "/admin/create-manager",
                        // URL danh sach va toggle trang thai account.
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
