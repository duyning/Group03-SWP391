/**
 * Interface Repository cung cấp các phương thức thao tác CSDL với bảng tài khoản (`account`).
 * 
 * Luồng gọi & Sử dụng:
 * - Gọi bởi `AccountService`, `AuthInterceptor`, `CustomerBookingService`, `VoucherService`.
 * - Hỗ trợ các chức năng: Đăng nhập/Đăng ký (`findByEmail`, `existsByEmail`), kiểm tra trùng số điện thoại (`existsByPhoneNumAndAccountIDNot`),
 *   load tài khoản kèm danh sách voucher đã lưu (`findByEmailWithVouchers`), tìm kiếm phân trang khách hàng (`searchCustomers`).
 * 
 * Khởi tạo bởi: DuongND_HE186619 (04/06/2026)
 */
package com.group3.cinema.repository;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Integer> {

    /**
     * Tìm kiếm tài khoản dựa theo địa chỉ email.
     * 
     * @param email Địa chỉ email tài khoản.
     * @return Account hoặc null nếu không tồn tại.
     */
    Account findByEmail(String email);

    /**
     * Tìm bản ghi tài khoản đầu tiên khớp với số điện thoại.
     * 
     * @param phoneNum Số điện thoại khách hàng.
     * @return Optional chứa Account nếu tìm thấy.
     */
    Optional<Account> findFirstByPhoneNum(String phoneNum);

    /**
     * Nạp đối tượng Account kèm tập hợp `savedVouchers` bằng câu lệnh JOIN FETCH.
     * Tránh lỗi LazyInitializationException khi làm việc với Session và hiển thị danh sách voucher đã lưu trong Hồ sơ cá nhân.
     * 
     * @param email Email đăng nhập của khách hàng.
     * @return Account đầy đủ thông tin voucher.
     */
    @Query("SELECT DISTINCT a FROM Account a LEFT JOIN FETCH a.savedVouchers WHERE a.email = :email")
    Account findByEmailWithVouchers(@Param("email") String email);

    /**
     * Kiểm tra xem địa chỉ email đã được đăng ký trong hệ thống hay chưa.
     */
    boolean existsByEmail(String email);

    /**
     * Kiểm tra xem số điện thoại đã được đăng ký bởi bất kỳ tài khoản nào hay chưa.
     */
    boolean existsByPhoneNum(String phoneNum);

    /**
     * Kiểm tra xem số điện thoại đã được đăng ký bởi tài khoản khác (ngoại trừ tài khoản đang chỉnh sửa `accountID`).
     */
    boolean existsByPhoneNumAndAccountIDNot(String phoneNum, Integer accountID);

    /**
     * Lấy danh sách tài khoản theo vai trò (Role) và đang ở trạng thái hoạt động (`status = true`).
     */
    List<Account> findByRoleAndStatusTrue(Role role);

    /**
     * Tìm kiếm danh sách khách hàng (`CUSTOMER`) hỗ trợ lọc theo từ khóa (tên, email, sđt) và sắp xếp theo tên A-Z.
     * 
     * @param role Vai trò cần tìm (thường là Role.CUSTOMER).
     * @param keyword Từ khóa tìm kiếm do Admin/Manager nhập.
     * @return Danh sách tài khoản khớp với từ khóa.
     */
    @Query("""
            SELECT a
            FROM Account a
            WHERE a.role = :role
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(a.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR a.phoneNum LIKE CONCAT('%', :keyword, '%'))
            ORDER BY a.name ASC
            """)
    List<Account> searchCustomers(@Param("role") Role role, @Param("keyword") String keyword);
}

