package com.group3.cinema.config;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

public class AuthInterceptor implements HandlerInterceptor {

    private final List<Role> allowedRoles;

    public AuthInterceptor() {
        this.allowedRoles = null;
    }

    public AuthInterceptor(Role... allowedRoles) {
        this.allowedRoles = Arrays.asList(allowedRoles);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("loggedInUser") == null) {
            session = request.getSession(true);
            String requestedUrl = request.getRequestURI();
            if (request.getQueryString() != null && !request.getQueryString().isBlank()) {
                requestedUrl += "?" + request.getQueryString();
            }
            session.setAttribute("redirectAfterLogin", requestedUrl);
            response.sendRedirect(request.getContextPath() + "/login");
            return false;
        }

        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return true;
        }

        Account account = (Account) session.getAttribute("loggedInUser");
        if (account.getRole() != null && allowedRoles.contains(account.getRole())) {
            return true;
        }

        response.sendRedirect(request.getContextPath() + "/access-denied");
        return false;
    }
}
