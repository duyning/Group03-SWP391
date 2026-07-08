package com.group3.cinema.dto;

/*
 * Added on 2026-06-24: Read model for one seat in the customer seat map.
 * Created by: HuyPB - HE191335
 */

import java.math.BigDecimal;

public record BookingSeatView(Long id,
                              int rowIndex,
                              int colIndex,
                              String label,
                              String type,
                              String displayName,
                              String color,
                              int capacity,
                              boolean sellable,
                              String status,
                              BigDecimal price) { }
