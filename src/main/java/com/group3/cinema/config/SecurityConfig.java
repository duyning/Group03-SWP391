/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
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

/**
 * Security táº¡m thá»i: táº¯t toÃ n bá»™ xÃ¡c thá»±c Ä‘á»ƒ phÃ¡t triá»ƒn frontend.
 * Má»i URL Ä‘á»u Ä‘Æ°á»£c truy cáº­p tá»± do, khÃ´ng cáº§n Ä‘Äƒng nháº­p.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Giá»¯ láº¡i PasswordEncoder Ä‘á»ƒ DataInitializer khÃ´ng bá»‹ lá»—i
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()   // Táº¥t cáº£ request Ä‘á»u Ä‘Æ°á»£c phÃ©p
            );
        return http.build();
    }
}

