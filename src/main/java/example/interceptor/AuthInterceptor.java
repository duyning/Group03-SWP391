package example.interceptor;

import example.entity.Account;
import example.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

/**
 * Interceptor kiểm tra quyền truy cập dựa trên trạng thái đăng nhập và vai trò (Role) của người dùng.
 * Nếu chưa đăng nhập → redirect về /login.
 * Nếu đã đăng nhập nhưng không đúng vai trò → redirect về /access-denied.
 *
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
public class AuthInterceptor implements HandlerInterceptor {

    private final List<Role> allowedRoles;

    /**
     * Constructor không tham số: chỉ kiểm tra đăng nhập, không kiểm tra role.
     */
    public AuthInterceptor() {
        this.allowedRoles = null;
    }

    /**
     * Constructor có tham số: kiểm tra đăng nhập VÀ role phải nằm trong danh sách cho phép.
     *
     * @param allowedRoles danh sách vai trò được phép truy cập
     */
    public AuthInterceptor(Role... allowedRoles) {
        this.allowedRoles = Arrays.asList(allowedRoles);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession session = request.getSession(false);

        // Kiểm tra đăng nhập
        if (session == null || session.getAttribute("loggedInUser") == null) {
            String contextPath = request.getContextPath();
            response.sendRedirect(contextPath + "/login");
            return false;
        }

        // Nếu không cần kiểm tra role → cho phép truy cập
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            return true;
        }

        // Kiểm tra role
        Account account = (Account) session.getAttribute("loggedInUser");
        if (account.getRole() != null && allowedRoles.contains(account.getRole())) {
            return true;
        }

        // Không có quyền → redirect về trang access-denied
        String contextPath = request.getContextPath();
        response.sendRedirect(contextPath + "/access-denied");
        return false;
    }
}
