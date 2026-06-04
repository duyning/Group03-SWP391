/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seat_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeatType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "display_name", nullable = false, columnDefinition = "NVARCHAR(80)")
    private String displayName;

    @Column(name = "color", nullable = false, length = 20)
    private String color;

    @Builder.Default
    @Column(name = "capacity", nullable = false)
    private int capacity = 1;

    @Builder.Default
    @Column(name = "sellable", nullable = false)
    private boolean sellable = true;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;
}
