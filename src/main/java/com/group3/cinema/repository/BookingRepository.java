package com.group3.cinema.repository;

/*
 * Added on 2026-06-24: Repository for customer booking records.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByIdAndAccountId(Long id, Integer accountId);
    List<Booking> findByStatusAndExpiresAtBefore(Booking.Status status, LocalDateTime expiresAt);
    long countByAccountIdAndVoucherCodeAndStatusIn(Integer accountId, String voucherCode, List<Booking.Status> statuses);

    @Query("""
            SELECT b
            FROM Booking b
            WHERE (:status IS NULL OR b.status = :status)
              AND (:fromDate IS NULL OR b.createdAt >= :fromDate)
              AND (:toDate IS NULL OR b.createdAt <= :toDate)
              AND (:paymentStatus IS NULL OR EXISTS (
                    SELECT p.id FROM Payment p
                    WHERE p.bookingId = b.id AND p.status = :paymentStatus
              ))
              AND (:paymentMethod IS NULL OR EXISTS (
                    SELECT p.id FROM Payment p
                    WHERE p.bookingId = b.id AND p.paymentMethod = :paymentMethod
              ))
              AND (:bookingId IS NULL OR b.id = :bookingId)
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(COALESCE(b.voucherCode, '')) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR EXISTS (
                        SELECT p.id FROM Payment p
                        WHERE p.bookingId = b.id
                          AND LOWER(p.orderCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   )
                   OR EXISTS (
                        SELECT a.accountID FROM Account a
                        WHERE a.accountID = b.accountId
                          AND (LOWER(a.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                               OR LOWER(a.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                               OR a.phoneNum LIKE CONCAT('%', :keyword, '%'))
                   ))
            ORDER BY b.createdAt DESC
            """)
    List<Booking> searchInvoices(@Param("keyword") String keyword,
                                 @Param("bookingId") Long bookingId,
                                 @Param("status") Booking.Status status,
                                 @Param("paymentStatus") com.group3.cinema.entity.Payment.Status paymentStatus,
                                 @Param("paymentMethod") com.group3.cinema.entity.Payment.Method paymentMethod,
                                 @Param("fromDate") LocalDateTime fromDate,
                                 @Param("toDate") LocalDateTime toDate);
}
