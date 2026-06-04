/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "audio_technologies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AudioTechnology {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, columnDefinition = "NVARCHAR(80)")
    private String name;

    @Column(name = "description", columnDefinition = "NVARCHAR(255)")
    private String description;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;
}
