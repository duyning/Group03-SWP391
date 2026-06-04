package example.controller;

import example.entity.Account;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller xử lý hiển thị trang chủ (Home Page).
 * Đăng nhập thành công sẽ hiển thị giao diện trang chủ với thông tin người dùng.
 * 
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
@Controller
public class HomeController {

    /**
     * Show home page. Redirects to login if not authenticated.
     */
    @GetMapping({"/", "/home"})
    public String showHome(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }
        return "home";
    }
}
