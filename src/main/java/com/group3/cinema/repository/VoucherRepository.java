package com.group3.cinema.repository;

import com.group3.cinema.entity.Voucher;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    // Tìm kiếm kiểm tra trùng mã khi THÊM MỚI
    boolean existsByCode(String code);

    // Tìm kiếm kiểm tra trùng mã với bản ghi KHÁC khi CẬP NHẬT
    boolean existsByCodeAndIdNot(String code, Long id);

    Optional<Voucher> findByCodeIgnoreCase(String code);

    // Lấy toàn bộ danh sách sắp xếp theo ID lớn nhất lên đầu (Mới nhất lên đầu)
    List<Voucher> findAllByOrderByIdDesc();

    // Câu Query JPQL lọc động, xử lý được cả trường hợp các trường truyền vào bị NULL (khi không chọn lọc)
    @Query("SELECT v FROM Voucher v WHERE " +
            "(:keyword IS NULL OR LOWER(v.code) LIKE LOWER(:keyword) OR LOWER(v.title) LIKE LOWER(:keyword)) AND " +
            "(:discountType IS NULL OR v.discountType = :discountType) AND " +
            "(:serviceScope IS NULL OR v.serviceScope = :serviceScope) " +
            "ORDER BY v.id DESC")
    List<Voucher> findVouchersByFilter(
            @Param("keyword") String keyword,
            @Param("discountType") Voucher.DiscountType discountType,
            @Param("serviceScope") Voucher.ServiceScope serviceScope);

    // Thêm hàm lấy voucher còn hạn sử dụng (ngày kết thúc lớn hơn thời gian hiện tại)
    @Query("SELECT v FROM Voucher v WHERE v.endDate > CURRENT_TIMESTAMP AND v.usedQuantity < v.totalQuantity ORDER BY v.endDate ASC")
    List<Voucher> findActiveVouchers();

    // Lấy danh sách voucher hiển thị cho KHÁCH: Chưa bị xóa mềm VÀ Hạn dùng phải sau thời gian hiện tại
    @Query("SELECT v FROM Voucher v WHERE v.isDeleted = false AND v.endDate > :now")
    List<Voucher> findActiveVouchers(@Param("now") LocalDateTime now);

    // Lấy danh sách cho ADMIN (Nếu muốn Admin vẫn thấy voucher hết hạn để xem thống kê, chỉ ẩn cái đã xóa mềm)
    List<Voucher> findByIsDeletedFalse();

    // Chỉ lấy voucher chưa xóa (IsDeletedFalse)
// VÀ còn hạn dùng (EndDateAfter)
// Đồng thời sắp xếp theo ID giảm dần (OrderByAccountIdDesc hoặc OrderByIdDesc tùy thuộc tính ID của cậu)
    List<Voucher> findByIsDeletedFalseAndEndDateAfterOrderByIdDesc(java.time.LocalDateTime now);
    // Câu lệnh tìm kiếm nâng cao dành riêng cho Admin (Luôn ẩn voucher đã xóa mềm)
    @Query("SELECT v FROM Voucher v WHERE v.isDeleted = false " +
            "AND (:keyword IS NULL OR LOWER(v.code) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(v.title) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:discountType IS NULL OR v.discountType = :discountType) " +
            "AND (:serviceScope IS NULL OR v.serviceScope = :serviceScope) " +
            "ORDER BY v.id DESC")
    List<Voucher> searchVouchersForAdmin(
            @Param("keyword") String keyword,
            @Param("discountType") Voucher.DiscountType discountType,
            @Param("serviceScope") Voucher.ServiceScope serviceScope
    );

    @Query(value = """
            SELECT CASE WHEN COUNT(1) > 0 THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END
            FROM account_vouchers
            WHERE account_id = :accountId AND voucher_id = :voucherId
            """, nativeQuery = true)
    boolean existsInWallet(@Param("accountId") int accountId, @Param("voucherId") Long voucherId);

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO account_vouchers (account_id, voucher_id)
            SELECT :accountId, :voucherId
            WHERE NOT EXISTS (
                SELECT 1
                FROM account_vouchers
                WHERE account_id = :accountId AND voucher_id = :voucherId
            )
            """, nativeQuery = true)
    int addToWallet(@Param("accountId") int accountId, @Param("voucherId") Long voucherId);

    @Query(value = """
            SELECT v.*
            FROM vouchers v
            INNER JOIN account_vouchers av ON av.voucher_id = v.id
            WHERE av.account_id = :accountId
            ORDER BY v.end_date ASC, v.id DESC
            """, nativeQuery = true)
    List<Voucher> findWalletVouchers(@Param("accountId") int accountId);

    @Query(value = """
            SELECT v.*
            FROM vouchers v
            INNER JOIN account_vouchers av ON av.voucher_id = v.id
            WHERE av.account_id = :accountId AND v.id = :voucherId
            """, nativeQuery = true)
    Optional<Voucher> findWalletVoucher(@Param("accountId") int accountId, @Param("voucherId") Long voucherId);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE vouchers
            SET used_quantity = ISNULL(used_quantity, 0) + 1
            WHERE UPPER(voucher_code) = UPPER(:code)
              AND ISNULL(used_quantity, 0) < total_quantity
            """, nativeQuery = true)
    int incrementUsedQuantityIfAvailable(@Param("code") String code);
}
