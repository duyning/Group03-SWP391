//package com.group3.cinema.config;
//
//import com.group3.cinema.entity.Account;
//import com.group3.cinema.entity.Movie;
//import com.group3.cinema.entity.Ticket;
//import com.group3.cinema.repository.AccountRepository;
//import com.group3.cinema.repository.MovieRepository;
//import com.group3.cinema.repository.TicketRepository;
//import jakarta.annotation.PostConstruct;
//import org.springframework.stereotype.Component;
//
//import java.math.BigDecimal;
//import java.time.LocalDate;
//import java.time.LocalDateTime;
//import java.time.LocalTime;
//import java.util.List;
//
///**
// * Tạo dữ liệu mẫu cho bảng tickets khi khởi động ứng dụng.
// * Chỉ seed nếu bảng tickets đang trống và có đủ dữ liệu account + movie.
// *
// * Ngày thực hiện: 26/06/2026
// */
//@Componet
//public class TicketSeedInitializer {
//
//    private final TicketRepository ticketRepository;
//    private final AccountRepository accountRepository;
//    private final MovieRepository movieRepository;
//
//    public TicketSeedInitializer(TicketRepository ticketRepository,
//            AccountRepository accountRepository,
//            MovieRepository movieRepository) {
//        this.ticketRepository = ticketRepository;
//        this.accountRepository = accountRepository;
//        this.movieRepository = movieRepository;
//    }
//
//    @PostConstruct
//    public void seedTickets() {
//        // Chỉ seed khi bảng tickets trống
//        if (ticketRepository.count() > 0) {
//            System.out.println("[TicketSeed] Bảng tickets đã có dữ liệu, bỏ qua seed.");
//            return;
//        }
//
//        List<Account> accounts = accountRepository.findAll();
//        List<Movie> movies = movieRepository.findAll();
//
//        if (accounts.isEmpty()) {
//            System.out.println("[TicketSeed] Chưa có account nào, bỏ qua seed tickets.");
//            return;
//        }
//        if (movies.isEmpty()) {
//            System.out.println("[TicketSeed] Chưa có movie nào, bỏ qua seed tickets.");
//            return;
//        }
//
//        // Tìm tài khoản Customer để gán vé (phù hợp chức năng "Vé của tôi" cho khách hàng)
//        Account customerAccount = accountRepository.findByEmail("customer@group03.com");
//        if (customerAccount == null) {
//            // Fallback: dùng account đầu tiên nếu không tìm thấy customer
//            customerAccount = accounts.get(0);
//        }
//        Account account2 = accounts.size() > 1 ? accounts.get(accounts.size() - 1) : customerAccount;
//
//        // Lấy tối đa 4 movie khác nhau
//        Movie movie1 = movies.get(0);
//        Movie movie2 = movies.size() > 1 ? movies.get(1) : movies.get(0);
//        Movie movie3 = movies.size() > 2 ? movies.get(2) : movies.get(0);
//        Movie movie4 = movies.size() > 3 ? movies.get(3) : movies.get(0);
//
//        LocalDate today = LocalDate.now();
//
//        // --- Vé 1: Đã xác nhận, sắp tới ---
//        Ticket t1 = new Ticket();
//        t1.setAccount(account1);
//        t1.setMovie(movie1);
//        t1.setRoomName("Phòng 01");
//        t1.setSeatLabel("A5");
//        t1.setSeatType("std");
//        t1.setShowDate(today.plusDays(2));
//        t1.setShowTime(LocalTime.of(19, 30));
//        t1.setPrice(new BigDecimal("85000"));
//        t1.setBookingTime(LocalDateTime.now().minusHours(3));
//        t1.setStatus("CONFIRMED");
//        t1.setPaymentMethod("Momo");
//        t1.setBookingCode("CF-" + today.toString().replace("-", "") + "-001");
//        ticketRepository.save(t1);
//
//        // --- Vé 2: Đã xác nhận, VIP ---
//        Ticket t2 = new Ticket();
//        t2.setAccount(account1);
//        t2.setMovie(movie2);
//        t2.setRoomName("Phòng 03");
//        t2.setSeatLabel("D8");
//        t2.setSeatType("vip");
//        t2.setShowDate(today.plusDays(5));
//        t2.setShowTime(LocalTime.of(21, 0));
//        t2.setPrice(new BigDecimal("120000"));
//        t2.setBookingTime(LocalDateTime.now().minusHours(1));
//        t2.setStatus("CONFIRMED");
//        t2.setPaymentMethod("ZaloPay");
//        t2.setBookingCode("CF-" + today.toString().replace("-", "") + "-002");
//        ticketRepository.save(t2);
//
//        // --- Vé 3: Đã sử dụng, quá khứ ---
//        Ticket t3 = new Ticket();
//        t3.setAccount(account1);
//        t3.setMovie(movie3);
//        t3.setRoomName("Phòng 02");
//        t3.setSeatLabel("B3");
//        t3.setSeatType("std");
//        t3.setShowDate(today.minusDays(7));
//        t3.setShowTime(LocalTime.of(14, 0));
//        t3.setPrice(new BigDecimal("75000"));
//        t3.setBookingTime(LocalDateTime.now().minusDays(8));
//        t3.setStatus("USED");
//        t3.setPaymentMethod("VNPay");
//        t3.setBookingCode("CF-" + today.minusDays(8).toString().replace("-", "") + "-003");
//        ticketRepository.save(t3);
//
//        // --- Vé 4: Đã hủy ---
//        Ticket t4 = new Ticket();
//        t4.setAccount(account1);
//        t4.setMovie(movie4);
//        t4.setRoomName("Phòng 05");
//        t4.setSeatLabel("C1-C2");
//        t4.setSeatType("couple");
//        t4.setShowDate(today.minusDays(3));
//        t4.setShowTime(LocalTime.of(20, 15));
//        t4.setPrice(new BigDecimal("200000"));
//        t4.setBookingTime(LocalDateTime.now().minusDays(5));
//        t4.setStatus("CANCELLED");
//        t4.setPaymentMethod("Tiền mặt");
//        t4.setBookingCode("CF-" + today.minusDays(5).toString().replace("-", "") + "-004");
//        ticketRepository.save(t4);
//
//        // --- Vé 5: Cho account2, đã xác nhận ---
//        Ticket t5 = new Ticket();
//        t5.setAccount(account2);
//        t5.setMovie(movie1);
//        t5.setRoomName("Phòng 02");
//        t5.setSeatLabel("E10");
//        t5.setSeatType("vip");
//        t5.setShowDate(today.plusDays(1));
//        t5.setShowTime(LocalTime.of(17, 45));
//        t5.setPrice(new BigDecimal("110000"));
//        t5.setBookingTime(LocalDateTime.now().minusMinutes(30));
//        t5.setStatus("CONFIRMED");
//        t5.setPaymentMethod("Momo");
//        t5.setBookingCode("CF-" + today.toString().replace("-", "") + "-005");
//        ticketRepository.save(t5);
//
//        // --- Vé 6: Cho account2, đã sử dụng ---
//        Ticket t6 = new Ticket();
//        t6.setAccount(account2);
//        t6.setMovie(movie2);
//        t6.setRoomName("Phòng 04");
//        t6.setSeatLabel("F7");
//        t6.setSeatType("std");
//        t6.setShowDate(today.minusDays(14));
//        t6.setShowTime(LocalTime.of(10, 30));
//        t6.setPrice(new BigDecimal("65000"));
//        t6.setBookingTime(LocalDateTime.now().minusDays(15));
//        t6.setStatus("USED");
//        t6.setPaymentMethod("ZaloPay");
//        t6.setBookingCode("CF-" + today.minusDays(15).toString().replace("-", "") + "-006");
//        ticketRepository.save(t6);
//
//        System.out.println("[TicketSeed] Đã tạo 6 vé mẫu thành công!");
//    }
//}
