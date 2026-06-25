package com.group3.cinema.repository;

/*
 * Added on 2026-06-24: Repository for booking payment transactions.
 * Created by: HuyPB - HE191335
 */

import com.group3.cinema.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderCode(String orderCode);
    Optional<Payment> findTopByBookingIdOrderByCreatedAtDesc(Long bookingId);
}
