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
                        "/forgot-password",
                        "/forgot-password/**",
                        "/reset-password",
                        "/reset-password/**",
                        "/access-denied",
                        "/movies",
                        "/movies/**",
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
