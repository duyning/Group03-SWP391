package com.group3.cinema.service.api;

/**
 * Dá»± Ã¡n: Cinema 2026 â€” SWP391 Group 03
 * File: ShowtimeService.java
 * Chá»©c nÄƒng: Lá»›p nghiá»‡p vá»¥ (Service) xá»­ lÃ½ cÃ¡c nghiá»‡p vá»¥ liÃªn quan Ä‘áº¿n lá»‹ch chiáº¿u (Showtime).
 *            Bao gá»“m: CRUD lá»‹ch chiáº¿u, lá»c tÃ¬m kiáº¿m, tá»± Ä‘á»™ng tÃ­nh toÃ¡n loáº¡i ngÃ y (NgÃ y trong tuáº§n/Cuá»‘i tuáº§n/NgÃ y lá»…)
 *            vÃ  xÃ¡c Ä‘á»‹nh giÃ¡ vÃ© cÆ¡ sá»Ÿ tÆ°Æ¡ng á»©ng cho tá»«ng lá»‹ch chiáº¿u.
 * NgÆ°á»i viáº¿t: Group 03 - SWP391
 * NgÃ y táº¡o: 2026-06-04
 */

import com.group3.cinema.entity.Showtime;
import com.group3.cinema.repository.api.ShowtimeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// ÄÃ¡nh dáº¥u lá»›p nÃ y lÃ  má»™t Service xá»­ lÃ½ nghiá»‡p vá»¥ quáº£n lÃ½ lá»‹ch chiáº¿u
@Service
public class ShowtimeService {

    private final ShowtimeRepository showtimeRepository;

    @Autowired
    public ShowtimeService(ShowtimeRepository showtimeRepository) {
        this.showtimeRepository = showtimeRepository;
    }

    // Láº¥y danh sÃ¡ch toÃ n bá»™ lá»‹ch chiáº¿u
    public List<Showtime> getAllShowtimes() {
        return showtimeRepository.findAll();
    }

    // Láº¥y chi tiáº¿t lá»‹ch chiáº¿u theo ID
    public Optional<Showtime> getShowtimeById(Long id) {
        return showtimeRepository.findById(id);
    }

    // TÃ¬m kiáº¿m lá»‹ch chiáº¿u káº¿t há»£p nhiá»u Ä‘iá»u kiá»‡n lá»c vÃ  ngÃ y chiáº¿u
    public List<Showtime> searchShowtimes(Integer movieId, String dayType, LocalDate startDate, LocalDate endDate) {
        return showtimeRepository.searchShowtimes(movieId, dayType, startDate, endDate);
    }

    // LÆ°u lá»‹ch chiáº¿u má»›i, tá»± Ä‘á»™ng phÃ¡t hiá»‡n loáº¡i ngÃ y chiáº¿u vÃ  táº¡o sÆ¡ Ä‘á»“ 40 gháº¿ ngá»“i (vÃ©)
    @Transactional
    public Showtime saveShowtime(Showtime showtime) {
        if (showtime.getShowDate() != null) {
            showtime.setDayType(determineDayType(showtime.getShowDate()));
        }
        Showtime savedShowtime = showtimeRepository.save(showtime);
        
        // Tá»± Ä‘á»™ng sinh 40 vÃ© ngá»“i trá»‘ng cho suáº¥t chiáº¿u má»›i nÃ y
//        generateTicketsForShowtime(savedShowtime);
        
        return savedShowtime;
    }

    // Cáº­p nháº­t thÃ´ng tin lá»‹ch chiáº¿u hiá»‡n cÃ³, tá»± tÃ­nh toÃ¡n láº¡i giÃ¡ vÃ© náº¿u ngÃ y chiáº¿u thay Ä‘á»•i loáº¡i ngÃ y
    @Transactional
    public Showtime updateShowtime(Long id, Showtime updatedShowtime) {
        return showtimeRepository.findById(id).map(showtime -> {
            showtime.setMovie(updatedShowtime.getMovie());
            
            // Xá»­ lÃ½ Ä‘á»•i ngÃ y chiáº¿u vÃ  tÃ­nh toÃ¡n láº¡i loáº¡i ngÃ y
            if (updatedShowtime.getShowDate() != null) {
                String oldDayType = showtime.getDayType();
                String newDayType = determineDayType(updatedShowtime.getShowDate());
                showtime.setShowDate(updatedShowtime.getShowDate());
                showtime.setDayType(newDayType);
            }
            
            showtime.setShowTime(updatedShowtime.getShowTime());
            showtime.setRoom(updatedShowtime.getRoom());
            
            return showtimeRepository.save(showtime);
        }).orElseThrow(() -> new RuntimeException("Showtime not found with id " + id));
    }

    // XÃ³a lá»‹ch chiáº¿u khá»i cÆ¡ sá»Ÿ dá»¯ liá»‡u (tá»± Ä‘á»™ng xÃ³a sáº¡ch vÃ© liÃªn quan nhá» CascadeType.ALL)
    public void deleteShowtime(Long id) {
        showtimeRepository.deleteById(id);
    }

    // Thá»‘ng kÃª sá»‘ lÆ°á»£ng lá»‹ch chiáº¿u theo tá»•ng sá»‘ vÃ  theo phÃ¢n loáº¡i ngÃ y
    public Map<String, Long> getShowtimeStats() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("total", showtimeRepository.count());
        stats.put("weekday", showtimeRepository.countByDayType("Trong tuáº§n"));
        stats.put("weekend", showtimeRepository.countByDayType("Cuá»‘i tuáº§n"));
        stats.put("holiday", showtimeRepository.countByDayType("NgÃ y lá»…"));
        return stats;
    }

    // HÃ m tiá»‡n Ã­ch xÃ¡c Ä‘á»‹nh loáº¡i ngÃ y tá»± Ä‘á»™ng dá»±a trÃªn ngÃ y Ä‘Æ°á»£c chá»n (dÆ°Æ¡ng lá»‹ch Viá»‡t Nam)
    public String determineDayType(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        // 1. Kiá»ƒm tra cÃ¡c ngÃ y lá»… lá»›n cá»‘ Ä‘á»‹nh theo DÆ°Æ¡ng lá»‹ch táº¡i Viá»‡t Nam
        if ((month == 1 && day == 1) ||    // Táº¿t DÆ°Æ¡ng Lá»‹ch
            (month == 4 && day == 30) ||   // NgÃ y Giáº£i PhÃ³ng Miá»n Nam 30/4
            (month == 5 && day == 1) ||    // NgÃ y Quá»‘c Táº¿ Lao Äá»™ng 1/5
            (month == 9 && day == 2)) {    // NgÃ y Quá»‘c KhÃ¡nh 2/9
            return "NgÃ y lá»…";
        }

        // 2. Kiá»ƒm tra náº¿u ngÃ y thuá»™c Thá»© 7 hoáº·c Chá»§ Nháº­t (Cuá»‘i tuáº§n)
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return "Cuá»‘i tuáº§n";
        }

        // 3. NgÃ y thÆ°á»ng (Trong tuáº§n: Thá»© 2 Ä‘áº¿n Thá»© 6)
        return "Trong tuáº§n";
    }

//    // Táº¡o 40 vÃ© cho sÆ¡ Ä‘á»“ gháº¿ cá»§a lá»‹ch chiáº¿u (A1-A8, B1-B8, C1-C8 lÃ  Gháº¿ thÆ°á»ng; D1-D8, E1-E8 lÃ  Gháº¿ VIP)
//    private void generateTicketsForShowtime(Showtime showtime) {
//        String[] rows = {"A", "B", "C", "D", "E"};
//        int seatsPerRow = 8;
//        String dayType = showtime.getDayType();
//
//        for (String row : rows) {
//            // HÃ ng D vÃ  E lÃ  gháº¿ VIP, cÃ²n láº¡i lÃ  gháº¿ thÆ°á»ng
//            String seatType = (row.equals("D") || row.equals("E")) ? "VIP" : "ThÆ°á»ng";
//            double price = calculateTicketPrice(dayType, seatType);
//
//
//        }
    }

//    // TÃ­nh toÃ¡n giÃ¡ vÃ© thá»±c táº¿ dá»±a theo ma tráº­n phÃ¢n loáº¡i ngÃ y vÃ  loáº¡i gháº¿
//    private double calculateTicketPrice(String dayType, String seatType) {
//        if ("Trong tuáº§n".equals(dayType)) {
//            return "VIP".equals(seatType) ? 100000.0 : 80000.0;
//        } else if ("Cuá»‘i tuáº§n".equals(dayType)) {
//            return "VIP".equals(seatType) ? 120000.0 : 100000.0;
//        } else if ("NgÃ y lá»…".equals(dayType)) {
//            return "VIP".equals(seatType) ? 140000.0 : 120000.0;
//        }
//        return "VIP".equals(seatType) ? 100000.0 : 80000.0;
//    }

