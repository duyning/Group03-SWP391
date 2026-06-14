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
                        "/search",
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
