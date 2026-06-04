/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity Ä‘áº¡i diá»‡n cho má»™t PhÃ²ng chiáº¿u (Room) trong ráº¡p chiáº¿u phim.
 */
@Entity
@Table(name = "rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** TÃªn phÃ²ng chiáº¿u, vÃ­ dá»¥: "PhÃ²ng 01", "Cinema 01" */
    @Column(name = "room_name", nullable = false, columnDefinition = "NVARCHAR(100)")
    private String roomName;

    /** Loáº¡i phÃ²ng: 2D, 3D, IMAX, Gold */
    @Column(name = "room_type", columnDefinition = "NVARCHAR(20)")
    @Builder.Default
    private String roomType = "2D";

    /** CÃ´ng nghá»‡ Ã¢m thanh: "Dolby 7.1", "Dolby Atmos", "DTS:X" */
    @Column(name = "audio_tech", columnDefinition = "NVARCHAR(50)")
    @Builder.Default
    private String audioTech = "Dolby 7.1";

    /** Sá»‘ hÃ ng gháº¿ (A, B, C...) */
    @Column(name = "rows", nullable = false)
    @Builder.Default
    private int rows = 8;

    /** Sá»‘ gháº¿ má»—i hÃ ng */
    @Column(name = "cols", nullable = false)
    @Builder.Default
    private int cols = 15;

    /** Tá»•ng sá»©c chá»©a, Ä‘Æ°á»£c cáº­p nháº­t sau khi lÆ°u sÆ¡ Ä‘á»“ gháº¿ */
    @Column(name = "total_seats")
    @Builder.Default
    private int totalSeats = 0;

    /** Tráº¡ng thÃ¡i: "Hoáº¡t Ä‘á»™ng", "Báº£o trÃ¬", "Táº¡m ngÆ°ng" */
    @Column(name = "status", columnDefinition = "NVARCHAR(20)")
    @Builder.Default
    private String status = "Hoáº¡t Ä‘á»™ng";

    /** ID ráº¡p chiáº¿u phim (foreign key, dÃ¹ng Ä‘Æ¡n giáº£n lÃ  Long) */
    @Column(name = "cinema_id")
    @Builder.Default
    private Long cinemaId = 1L;

}
