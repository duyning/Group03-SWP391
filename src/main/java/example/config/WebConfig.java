package example.config;

import example.entity.Role;
import example.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Cấu hình Web MVC: đăng ký các Interceptor phân quyền theo URL patterns.
 * - Interceptor 1: Yêu cầu đăng nhập cho tất cả trang (trừ login, register, forgot-password)
 * - Interceptor 2: Yêu cầu role ADMIN hoặc MANAGER cho các trang /admin/**
 *
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        // Interceptor 1: Kiểm tra đăng nhập cho tất cả trang
        // Loại trừ các trang công khai (login, register, forgot-password, access-denied, static resources)
        registry.addInterceptor(new AuthInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login",
                        "/register",
                        "/forgot-password",
                        "/forgot-password/**",
                        "/access-denied",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/fonts/**",
                        "/error"
                );

        // Interceptor 2: Kiểm tra role ADMIN hoặc MANAGER cho trang quản lý
        // ADMIN và MANAGER tạm thời có quyền ngang nhau
        registry.addInterceptor(new AuthInterceptor(Role.ADMIN, Role.MANAGER))
                .addPathPatterns("/admin/**");
    }
}
