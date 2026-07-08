package com.group3.cinema.repository;

import com.group3.cinema.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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

    /**
     * Load account kèm theo savedVouchers (JOIN FETCH) để tránh LazyInitializationException
     * khi Account được lưu vào HTTP session sau khi Hibernate session đã đóng.
     */
    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.savedVouchers WHERE a.email = :email")
    Account findByEmailWithVouchers(@Param("email") String email);

    boolean existsByEmail(String email);

    boolean existsByPhoneNum(String phoneNum);

    boolean existsByPhoneNumAndAccountIDNot(String phoneNum, Integer accountID);
}
