/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity Ä‘áº¡i diá»‡n cho má»™t Ã´ gháº¿ trong sÆ¡ Ä‘á»“ phÃ²ng chiáº¿u.
 * Má»—i gháº¿ thuá»™c vá» má»™t Room vÃ  Ä‘Æ°á»£c xÃ¡c Ä‘á»‹nh bá»Ÿi (rowIndex, colIndex).
 * type: "std" | "vip" | "couple" | "broken" | "empty" | "skip"
 */
@Entity
@Table(name = "seats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "row_index", "col_index"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** PhÃ²ng chiáº¿u sá»Ÿ há»¯u gháº¿ nÃ y */
    @Column(name = "room_id", nullable = false)
    private Long roomId;

    /** Chá»‰ sá»‘ hÃ ng (0-based), 0 = hÃ ng A */
    @Column(name = "row_index", nullable = false)
    private int rowIndex;

    /** Chá»‰ sá»‘ cá»™t (0-based) */
    @Column(name = "col_index", nullable = false)
    private int colIndex;

    /**
     * NhÃ£n gháº¿ hiá»ƒn thá»‹, vÃ­ dá»¥ "A1", "B12", "C3-C4" (couple).
     * ÄÆ°á»£c tÃ­nh tá»± Ä‘á»™ng, khÃ´ng pháº£i ngÆ°á»i dÃ¹ng nháº­p.
     */
    @Column(name = "seat_label", columnDefinition = "NVARCHAR(20)")
    private String seatLabel;

    /**
     * Loáº¡i gháº¿:
     * - "std"    : Gháº¿ thÆ°á»ng
     * - "vip"    : Gháº¿ VIP
     * - "couple" : Gháº¿ Ä‘Ã´i (chiáº¿m 2 cá»™t liá»n ká», cá»™t káº¿ tiáº¿p sáº½ lÃ  "skip")
     * - "broken" : Gháº¿ há»ng / báº£o trÃ¬
     * - "empty"  : Lá»‘i Ä‘i / Ã´ trá»‘ng
     * - "skip"   : Ã” bá»‹ chiáº¿m bá»Ÿi couple bÃªn trÃ¡i (khÃ´ng render)
     */
    @Column(name = "seat_type", nullable = false, columnDefinition = "NVARCHAR(30)")
    @Builder.Default
    private String seatType = "std";
}
