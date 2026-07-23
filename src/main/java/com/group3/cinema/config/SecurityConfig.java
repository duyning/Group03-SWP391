/**
 * Lớp cấu hình bảo mật Spring Security (SecurityConfig).
 * 
 * Do ứng dụng tự quản lý đăng nhập và xác thực phiên qua HttpSession (`AuthInterceptor`),
 * lớp này cấu hình Spring Security ở mức tối giản:
 * - Cung cấp Bean BCryptPasswordEncoder để mã hóa mật khẩu người dùng.
 * - Tắt các tính năng mặc định như CSRF, Form Login mặc định, HTTP Basic để nhường quyền kiểm soát cho WebConfig và Interceptor.
 * 
 * Ngày cập nhật: 04/06/2026
 * Khởi tạo bởi: NinhDD - HE186113
 */
package com.group3.cinema.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;


@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Tạo Bean PasswordEncoder sử dụng giải thuật BCrypt.
     * 
     * Được gọi bởi `AccountService`, `LoginController`, `RegisterController` để mã hóa mật khẩu khi đăng ký
     * hoặc so sánh mật khẩu nhập vào với mật khẩu đã lưu trong database.
     * 
     * @return Đối tượng BCryptPasswordEncoder.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Cấu hình chuỗi lọc bảo mật Spring Security (SecurityFilterChain).
     * 
     * Cấu hình:
     * - Vô hiệu hóa CSRF, Form Login, HTTP Basic và Logout mặc định của Spring Security.
     * - Bỏ qua bảo vệ phân quyền mặc định (`anyRequest().permitAll()`), nhường toàn bộ việc phân quyền URL cho `AuthInterceptor`.
     * 
     * @param http Đối tượng HttpSecurity để thiết lập bảo mật.
     * @return SecurityFilterChain đối tượng chuỗi lọc đã tạo.
     * @throws Exception Ngoại lệ nếu cấu hình lỗi.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        return http.build();
    }
}


