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
                .addPathPatterns("/admin/**", "/manage_movies.html", "/manage_showtime.html", "/manage_ticket.html");
    }
}
