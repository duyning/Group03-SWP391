package example.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller xử lý trang thông báo không có quyền truy cập (Access Denied).
 *
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
@Controller
public class AccessDeniedController {

    @GetMapping("/access-denied")
    public String showAccessDenied() {
        return "access-denied";
    }
}
