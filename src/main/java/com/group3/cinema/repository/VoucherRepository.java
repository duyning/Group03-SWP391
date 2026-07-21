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

/**
 * Repository quản lý dữ liệu cho Entity Voucher (Mã giảm giá / Ưu đãi).
 * Thực hiện các thao tác CRUD, tìm kiếm/lọc đa điều kiện (JPQL) và quản lý Ví Voucher tài khoản (Native Query).
 *
 * @author Group 3 - Cinema Management System
 */
@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    /**
     * Kiểm tra sự tồn tại của mã Voucher theo mã Code.
     * Phục vụ kiểm tra trùng mã khi THÊM MỚI Voucher.
     */
    boolean existsByCode(String code);

    /**
     * Kiểm tra xem mã Code đã được sử dụng bởi một Voucher KHÁC hay chưa.
     * Phục vụ kiểm tra trùng mã khi CẬP NHẬT (Chỉnh sửa) Voucher.
     */
    boolean existsByCodeAndIdNot(String code, Long id);

    /**
     * Tìm kiếm Voucher theo mã Code (Không phân biệt chữ hoa/chữ thường).
     */
    Optional<Voucher> findByCodeIgnoreCase(String code);

    /**
     * Lấy toàn bộ danh sách Voucher, sắp xếp theo ID giảm dần (Bản ghi mới nhất lên đầu).
     */
    List<Voucher> findAllByOrderByIdDesc();

    /**
     * Truy vấn lọc động Voucher (JPQL) hỗ trợ xử lý linh hoạt khi các trường tham số bị NULL.
     * Lọc theo Từ khóa (mã hoặc tiêu đề), Loại giảm giá và Phạm vi dịch vụ.
     */
    @Query("SELECT v FROM Voucher v WHERE " +
            "(:keyword IS NULL OR LOWER(v.code) LIKE LOWER(:keyword) OR LOWER(v.title) LIKE LOWER(:keyword)) AND " +
            "(:discountType IS NULL OR v.discountType = :discountType) AND " +
            "(:serviceScope IS NULL OR v.serviceScope = :serviceScope) " +
            "ORDER BY v.id DESC")
    List<Voucher> findVouchersByFilter(
            @Param("keyword") String keyword,
            @Param("discountType") Voucher.DiscountType discountType,
            @Param("serviceScope") Voucher.ServiceScope serviceScope);

    /**
     * Lấy danh sách Voucher còn hạn sử dụng (Thời gian kết thúc > Hiện tại) và chưa hết lượt dùng.
     * Sắp xếp ưu tiên các Voucher sắp hết hạn lên trước.
     */
    @Query("SELECT v FROM Voucher v WHERE v.endDate > CURRENT_TIMESTAMP AND v.usedQuantity < v.totalQuantity ORDER BY v.endDate ASC")
    List<Voucher> findActiveVouchers();

    /**
     * Lấy danh sách Voucher hiển thị cho KHÁCH HÀNG: Chưa bị xóa mềm (isDeleted = false)
     * VÀ thời gian kết thúc phải lớn hơn thời điểm truyền vào (:now).
     */
    @Query("SELECT v FROM Voucher v WHERE v.isDeleted = false AND v.endDate > :now")
    List<Voucher> findActiveVouchers(@Param("now") LocalDateTime now);

    /**
     * Lấy danh sách Voucher chưa bị xóa mềm cho Quản trị viên (Admin viewing).
     */
    List<Voucher> findByIsDeletedFalse();

    /**
     * Lấy danh sách Voucher chưa bị xóa mềm, còn hạn dùng và sắp xếp ID giảm dần (Giao diện hiển thị).
     */
    List<Voucher> findByIsDeletedFalseAndEndDateAfterOrderByIdDesc(java.time.LocalDateTime now);

    /**
     * Truy vấn tìm kiếm nâng cao dành riêng cho Admin Dashboard.
     * Luôn lọc bỏ các mã đã xóa mềm (isDeleted = false) và hỗ trợ tìm kiếm theo Từ khóa, Loại giảm giá, Phạm vi dịch vụ.
     */
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

    /**
     * Native Query: Kiểm tra xem Voucher đã có trong Ví của Tài khoản (Bảng trung gian account_vouchers) hay chưa.
     */
    @Query(value = """
            SELECT CASE WHEN COUNT(1) > 0 THEN CAST(1 AS bit) ELSE CAST(0 AS bit) END
            FROM account_vouchers
            WHERE account_id = :accountId AND voucher_id = :voucherId
            """, nativeQuery = true)
    boolean existsInWallet(@Param("accountId") int accountId, @Param("voucherId") Long voucherId);

    /**
     * Native Query: Thêm Voucher vào Ví người dùng (Bảng account_vouchers).
     * Sử dụng WHERE NOT EXISTS để chống trùng lặp dữ liệu trong database.
     */
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

    /**
     * Native Query: Lấy toàn bộ danh sách Voucher nằm trong Ví cá nhân của một Tài khoản cụ thể.
     */
    @Query(value = """
            SELECT v.*
            FROM vouchers v
            INNER JOIN account_vouchers av ON av.voucher_id = v.id
            WHERE av.account_id = :accountId
            ORDER BY v.end_date ASC, v.id DESC
            """, nativeQuery = true)
    List<Voucher> findWalletVouchers(@Param("accountId") int accountId);

    /**
     * Native Query: Kiểm tra thông tin một Voucher cụ thể trong Ví của người dùng.
     */
    @Query(value = """
            SELECT v.*
            FROM vouchers v
            INNER JOIN account_vouchers av ON av.voucher_id = v.id
            WHERE av.account_id = :accountId AND v.id = :voucherId
            """, nativeQuery = true)
    Optional<Voucher> findWalletVoucher(@Param("accountId") int accountId, @Param("voucherId") Long voucherId);

    /**
     * Native Query: Tăng số lượng đã sử dụng (used_quantity) lên 1 khi người dùng áp dụng Voucher thành công.
     * Đảm bảo điều kiện an toàn: Số lượng đã dùng phải nhỏ hơn tổng số lượng (total_quantity).
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