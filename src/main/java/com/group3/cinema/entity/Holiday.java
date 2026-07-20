package com.group3.cinema.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "holidays", uniqueConstraints = {
        @UniqueConstraint(columnNames = "holiday_date", name = "uq_holiday_date")
})
public class Holiday {

    public Holiday() {
    }

    public Holiday(Long id, String name, LocalDate holidayDate, LocalDateTime createdAt) {
        this.id = id;
        this.name = name;
        this.holidayDate = holidayDate;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getHolidayDate() {
        return holidayDate;
    }

    public void setHolidayDate(LocalDate holidayDate) {
        this.holidayDate = holidayDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 1. TÊN NGÀY LỄ (Hỗ trợ tiếng Việt có dấu dưới SQL Server)
    @NotBlank(message = "Tên ngày lễ không được để trống")
    @Size(max = 255, message = "Tên ngày lễ không được quá 255 ký tự")
    @Column(name = "holiday_name", nullable = false, columnDefinition = "NVARCHAR(255)")
    private String name; // Ví dụ: "Tết Dương Lịch", "Nghỉ bù Giỗ Tổ", "Dịp Tết Âm Lịch"

    // 2. NGÀY DƯƠNG LỊCH CỤ THỂ
    @NotNull(message = "Vui lòng chọn ngày áp dụng lễ")
    @Column(name = "holiday_date", nullable = false)
    private LocalDate holidayDate; // Chỉ cần lưu Ngày/Tháng/Năm để đối chiếu với ngày đặt vé

    // 3. THỜI GIAN TẠO (Audit Log tự động)
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // Tự động bắt thời gian hệ thống ngay khi Admin bấm thêm mới ngày lễ
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}