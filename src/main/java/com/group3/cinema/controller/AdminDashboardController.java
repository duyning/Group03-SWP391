package com.group3.cinema.controller;

/*
 * Created on 2026-06-09: Added admin dashboard route for manager/admin login flow.
 * Created by: NinhDD - HE186113
 */

import com.group3.cinema.entity.Account;
import com.group3.cinema.entity.Booking;
import com.group3.cinema.entity.Room;
import com.group3.cinema.entity.Showtime;
import com.group3.cinema.entity.Ticket;
import com.group3.cinema.repository.*;
import com.group3.cinema.repository.api.ShowtimeRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class AdminDashboardController {

    private final MovieRepository movieRepository;
    private final RoomRepository roomRepository;
    private final SeatRepository seatRepository;
    private final ShowtimeRepository showtimeRepository;
    private final BookingRepository bookingRepository;
    private final AccountRepository accountRepository;
    private final BookingTicketRepository bookingTicketRepository;
    private final TicketRepository ticketRepository;

    public AdminDashboardController(MovieRepository movieRepository,
                                    RoomRepository roomRepository,
                                    SeatRepository seatRepository,
                                    ShowtimeRepository showtimeRepository,
                                    BookingRepository bookingRepository,
                                    AccountRepository accountRepository,
                                    BookingTicketRepository bookingTicketRepository,
                                    TicketRepository ticketRepository) {
        this.movieRepository = movieRepository;
        this.roomRepository = roomRepository;
        this.seatRepository = seatRepository;
        this.showtimeRepository = showtimeRepository;
        this.bookingRepository = bookingRepository;
        this.accountRepository = accountRepository;
        this.bookingTicketRepository = bookingTicketRepository;
        this.ticketRepository = ticketRepository;
    }

    public record BookingDto(
        Long id,
        String customerName,
        String movieTitle,
        int ticketQuantity,
        BigDecimal totalPrice,
        String bookingTime,
        String status
    ) {}

    public record MovieRevenueDto(
        String title,
        double revenue,
        long ticketsSold
    ) {}

    public record PaymentMethodStatDto(
        String method,
        double revenue,
        long count
    ) {}

    @GetMapping("/admin/dashboard")
    public String showDashboard(HttpSession session, Model model) {
        Account loggedInUser = (Account) session.getAttribute("loggedInUser");
        if (loggedInUser != null) {
            model.addAttribute("user", loggedInUser);
        }

        // Fetch counts for entities
        model.addAttribute("movieCount", movieRepository.count());
        model.addAttribute("roomCount", roomRepository.count());
        model.addAttribute("seatCount", seatRepository.count());
        model.addAttribute("showtimeCount", showtimeRepository.count());

        // Fetch all bookings and tickets
        List<Booking> allBookings = bookingRepository.findAll();
        List<Ticket> allTickets = ticketRepository.findAll();

        // 1. Filter sold tickets: status is CONFIRMED, BOOKED, USED, or Đã bán
        Set<String> soldStatuses = Set.of("CONFIRMED", "BOOKED", "USED", "Đã bán");
        List<Ticket> soldTickets = allTickets.stream()
                .filter(t -> !t.isDeleted() && soldStatuses.contains(t.getStatus()))
                .collect(Collectors.toList());

        // 2. Total tickets sold count
        long totalTicketsSold = soldTickets.size();
        model.addAttribute("totalTicketsSold", totalTicketsSold);

        // 3. Paid bookings count
        long totalBookingsPaid = allBookings.stream()
                .filter(b -> b.getStatus() == Booking.Status.PAID)
                .count();
        model.addAttribute("totalBookingsPaid", totalBookingsPaid);

        // 4. Calculate total revenue: Sum of finalPrice of all sold tickets + Sum of comboSubtotal of PAID bookings
        double ticketRevenue = soldTickets.stream().mapToDouble(Ticket::getFinalPrice).sum();
        double comboRevenue = allBookings.stream()
                .filter(b -> b.getStatus() == Booking.Status.PAID)
                .mapToDouble(b -> b.getComboSubtotal() != null ? b.getComboSubtotal().doubleValue() : 0.0)
                .sum();
        double totalRevenue = ticketRevenue + comboRevenue;
        model.addAttribute("totalRevenue", totalRevenue);

        // 5. Calculate Occupancy Rate
        List<Showtime> showtimes = showtimeRepository.findAll();
        long totalCapacity = showtimes.stream()
                .mapToLong(s -> roomRepository.findFirstByRoomNameIgnoreCase(s.getRoom()).map(Room::getTotalSeats).orElse(0))
                .sum();
        double occupancyRate = totalCapacity > 0 ? ((double) totalTicketsSold / totalCapacity) * 100 : 0.0;
        model.addAttribute("occupancyRate", Math.round(occupancyRate * 10.0) / 10.0);

        // 6. Top 5 Movies by Revenue
        Map<String, MovieRevenueDto> movieStats = new HashMap<>();
        for (Ticket t : soldTickets) {
            String title = t.getMovie() != null ? t.getMovie().getTitle() : "N/A";
            double price = t.getFinalPrice();
            movieStats.merge(title, new MovieRevenueDto(title, price, 1), (oldVal, newVal) ->
                new MovieRevenueDto(title, oldVal.revenue() + newVal.revenue(), oldVal.ticketsSold() + 1)
            );
        }
        List<MovieRevenueDto> topMovies = movieStats.values().stream()
                .sorted((m1, m2) -> Double.compare(m2.revenue(), m1.revenue()))
                .limit(5)
                .collect(Collectors.toList());
        model.addAttribute("topMovies", topMovies);

        // 7. Payment Statistics (Group tickets by method)
        Map<String, PaymentMethodStatDto> paymentStats = new HashMap<>();
        for (Ticket t : soldTickets) {
            String rawMethod = t.getPaymentMethod();
            String method = "Tiền mặt / Khác";
            if (rawMethod != null && !rawMethod.isBlank()) {
                String upper = rawMethod.trim().toUpperCase();
                method = switch (upper) {
                    case "PAYOS" -> "PayOS";
                    case "VNPAY" -> "VNPay";
                    case "MOMO" -> "MoMo";
                    case "CASH" -> "Tiền mặt";
                    default -> rawMethod;
                };
            }
            double price = t.getFinalPrice();
            final String finalMethod = method;
            paymentStats.merge(finalMethod, new PaymentMethodStatDto(finalMethod, price, 1), (oldVal, newVal) ->
                new PaymentMethodStatDto(finalMethod, oldVal.revenue() + newVal.revenue(), oldVal.count() + 1)
            );
        }
        model.addAttribute("paymentStats", paymentStats.values());

        // 8. Recent bookings history list
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        List<BookingDto> bookingHistory = allBookings.stream()
                .map(b -> {
                    String custName = accountRepository.findById(b.getAccountId())
                            .map(Account::getName)
                            .orElse("Khách vãng lai");
                    String movieTitle = showtimeRepository.findById(b.getShowtimeId())
                            .map(s -> s.getMovie().getTitle())
                            .orElse("N/A");
                    int ticketQty = bookingTicketRepository.findByBookingId(b.getId()).size();
                    String timeStr = b.getCreatedAt().format(formatter);
                    String statusLabel = switch (b.getStatus()) {
                        case PAID -> "Đã thanh toán";
                        case PENDING -> "Chờ thanh toán";
                        case CANCELLED -> "Đã hủy";
                        case EXPIRED -> "Hết hạn";
                    };
                    return new BookingDto(b.getId(), custName, movieTitle, ticketQty, b.getTotalAmount(), timeStr, statusLabel);
                })
                .sorted((b1, b2) -> b2.id().compareTo(b1.id())) // Latest first
                .collect(Collectors.toList());
        model.addAttribute("bookings", bookingHistory);

        model.addAttribute("active", "dashboard");
        return "admin_dashboard";
    }
}
