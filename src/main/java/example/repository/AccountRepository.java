package example.repository;

import example.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
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

    boolean existsByEmail(String email);

    boolean existsByPhoneNum(String phoneNum);
}
