package com.group3.cinema.repository;

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository cung cấp các phương thức thao tác dữ liệu với bảng account trong cơ sở dữ liệu.
 * Kế thừa JpaRepository để cung cấp các hàm CRUD cơ bản và các truy vấn tìm kiếm/kiểm tra email, số điện thoại.
 * 
 * Ngày thực hiện: 04/06/2026
 * Tạo bởi: DuongND_HE186619
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Integer> {

    Account findByEmail(String email);

    Optional<Account> findFirstByPhoneNum(String phoneNum);

    /**
     * Load account kèm theo savedVouchers (JOIN FETCH) để tránh LazyInitializationException
     * khi Account được lưu vào HTTP session sau khi Hibernate session đã đóng.
     */
    @Query("SELECT DISTINCT a FROM Account a LEFT JOIN FETCH a.savedVouchers WHERE a.email = :email")
    Account findByEmailWithVouchers(@Param("email") String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNum(String phoneNum);

    boolean existsByPhoneNumAndAccountIDNot(String phoneNum, Integer accountID);

    List<Account> findByRoleAndStatusTrue(Role role);

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
