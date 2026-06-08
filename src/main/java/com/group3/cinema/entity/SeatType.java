/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "seat_types")
public class SeatType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, columnDefinition = "NVARCHAR(20)")
    private String code;

    @Column(name = "display_name", nullable = false, columnDefinition = "NVARCHAR(80)")
    private String displayName;

    @Column(name = "color", nullable = false, length = 20)
    private String color;

    @Column(name = "capacity", nullable = false)
    private int capacity = 1;

    @Column(name = "sellable", nullable = false)
    private boolean sellable = true;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public SeatType() {
    }

    public SeatType(Long id, String code, String displayName, String color,
                    int capacity, boolean sellable, boolean active) {
        this.id = id;
        this.code = code;
        this.displayName = displayName;
        this.color = color;
        this.capacity = capacity;
        this.sellable = sellable;
        this.active = active;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final SeatType seatType = new SeatType();

        public Builder id(Long id) {
            seatType.setId(id);
            return this;
        }

        public Builder code(String code) {
            seatType.setCode(code);
            return this;
        }

        public Builder displayName(String displayName) {
            seatType.setDisplayName(displayName);
            return this;
        }

        public Builder color(String color) {
            seatType.setColor(color);
            return this;
        }

        public Builder capacity(int capacity) {
            seatType.setCapacity(capacity);
            return this;
        }

        public Builder sellable(boolean sellable) {
            seatType.setSellable(sellable);
            return this;
        }

        public Builder active(boolean active) {
            seatType.setActive(active);
            return this;
        }

        public SeatType build() {
            return seatType;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public boolean isSellable() {
        return sellable;
    }

    public void setSellable(boolean sellable) {
        this.sellable = sellable;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
