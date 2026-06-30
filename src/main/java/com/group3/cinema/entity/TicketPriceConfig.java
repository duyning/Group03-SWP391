/*
 * Dự án: Cinema 2026 — SWP391 Group 03
 * File: TicketPriceConfig.java
 * Người tạo: TrienLX
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalTime;

@Entity
@Table(name = "ticket_price_configs",
       uniqueConstraints = @UniqueConstraint(columnNames = {"day_type", "slot_name"}))
public class TicketPriceConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "day_type", nullable = false, length = 30)
    private String dayType;   // "Trong tuần" | "Cuối tuần" | "Ngày lễ"

    @Column(name = "slot_name", nullable = false, length = 30)
    private String slotName;  // "Suất sớm" | "Giờ thường" | "Giờ vàng" | "Suất khuya"

    @Column(name = "start_time", nullable = false)
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;

    @Column(name = "base_price", nullable = false)
    private double basePrice;

    public TicketPriceConfig() {}

    public TicketPriceConfig(String dayType, String slotName, LocalTime startTime, LocalTime endTime, double basePrice) {
        this.dayType = dayType;
        this.slotName = slotName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.basePrice = basePrice;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDayType() { return dayType; }
    public void setDayType(String dayType) { this.dayType = dayType; }

    public String getSlotName() { return slotName; }
    public void setSlotName(String slotName) { this.slotName = slotName; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public double getBasePrice() { return basePrice; }
    public void setBasePrice(double basePrice) { this.basePrice = basePrice; }
}
