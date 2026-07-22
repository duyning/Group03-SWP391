/**
 * Interface Repository quản lý thông tin Mã giảm giá / Voucher khuyến mãi (`vouchers`) và Ví Voucher khách hàng (`account_vouchers`).
 * 
 * Luồng gọi & Sử dụng:
 * - Được gọi bởi `VoucherService`, `CustomerBookingService`, `PaymentSchemaMigration`.
 * - Hỗ trợ các chức năng: Tìm kiếm và lọc mã giảm giá phía Admin (`searchVouchersForAdmin`),
 *   lấy danh sách voucher áp dụng cho phía khách hàng (`findActiveVouchers`),
 *   lưu/kiểm tra voucher trong ví tài khoản (`addToWallet`, `existsInWallet`, `findWalletVouchers`),
 *   và tăng số lượt sử dụng voucher nguyên tử khi thanh toán thành công (`incrementUsedQuantityIfAvailable`).
 */
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

    /** Kiểm tra xem mã voucher đã tồn tại chưa khi tạo mới. */
    boolean existsByCode(String code);

    /** Kiểm tra xem mã voucher có trùng với bản ghi khác (ngoại trừ ID `id`) khi cập nhật. */
    boolean existsByCodeAndIdNot(String code, Long id);

    /** Tìm voucher theo mã code (không phân biệt chữ hoa/thường). */
    Optional<Voucher> findByCodeIgnoreCase(String code);

    /** Lấy toàn bộ danh sách voucher sắp xếp ID giảm dần (mới nhất lên đầu). */
    List<Voucher> findAllByOrderByIdDesc();

    /** Lọc danh sách voucher theo từ khóa, loại chiết khấu và phạm vi dịch vụ áp dụng. */
    @Query("SELECT v FROM Voucher v WHERE " +
            "(:keyword IS NULL OR LOWER(v.code) LIKE LOWER(:keyword) OR LOWER(v.title) LIKE LOWER(:keyword)) AND " +
            "(:discountType IS NULL OR v.discountType = :discountType) AND " +
            "(:serviceScope IS NULL OR v.serviceScope = :serviceScope) " +
            "ORDER BY v.id DESC")
    List<Voucher> findVouchersByFilter(
            @Param("keyword") String keyword,
            @Param("discountType") Voucher.DiscountType discountType,
            @Param("serviceScope") Voucher.ServiceScope serviceScope);

    /** Lấy danh sách các voucher đang hoạt động và còn số lượng lượt sử dụng. */
    @Query("SELECT v FROM Voucher v WHERE v.endDate > CURRENT_TIMESTAMP AND v.usedQuantity < v.totalQuantity ORDER BY v.endDate ASC")
    List<Voucher> findActiveVouchers();

    /** Lấy danh sách voucher công khai chưa bị xóa mềm và còn hạn dùng so với mốc thời gian `now`. */
    @Query("SELECT v FROM Voucher v WHERE v.isDeleted = false AND v.endDate > :now")
    List<Voucher> findActiveVouchers(@Param("now") LocalDateTime now);

    /** Lấy danh sách tất cả voucher chưa bị xóa mềm cho trang quản trị Admin. */
    List<Voucher> findByIsDeletedFalse();

    /** Lấy danh sách voucher chưa xóa mềm, còn hạn dùng và sắp xếp ID giảm dần. */
    List<Voucher> findByIsDeletedFalseAndEndDateAfterOrderByIdDesc(java.time.LocalDateTime now);

    /** Tìm kiếm nâng cao danh sách voucher cho Admin (tự động bỏ qua các bản ghi xóa mềm). */
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

    /** Kiểm tra xem một voucher đã được tài khoản lưu vào Ví Voucher cá nhân hay chưa. */
    @Query(value = """
            SELECT CASE WHEN COUNT(1) > 0 THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END
            FROM account_vouchers
            WHERE account_id = :accountId AND voucher_id = :voucherId
            """, nativeQuery = true)
    boolean existsInWallet(@Param("accountId") int accountId, @Param("voucherId") Long voucherId);

    /** Thêm một voucher vào Ví Voucher của tài khoản (tránh lưu trùng lặp). */
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

    /** Lấy danh sách các voucher có trong ví của tài khoản. */
    @Query(value = """
            SELECT v.*
            FROM vouchers v
            INNER JOIN account_vouchers av ON av.voucher_id = v.id
            WHERE av.account_id = :accountId
            ORDER BY v.end_date ASC, v.id DESC
            """, nativeQuery = true)
    List<Voucher> findWalletVouchers(@Param("accountId") int accountId);

    /** Lấy thông tin một voucher cụ thể trong ví tài khoản. */
    @Query(value = """
            SELECT v.*
            FROM vouchers v
            INNER JOIN account_vouchers av ON av.voucher_id = v.id
            WHERE av.account_id = :accountId AND v.id = :voucherId
            """, nativeQuery = true)
    Optional<Voucher> findWalletVoucher(@Param("accountId") int accountId, @Param("voucherId") Long voucherId);

    /**
     * Tăng số lượng đã sử dụng (`usedQuantity`) lên 1 theo cách nguyên tử (Atomic Update) khi áp dụng voucher thành công.
     */
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

