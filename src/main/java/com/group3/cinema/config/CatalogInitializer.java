/*
 * Updated on 2026-06-04: Added project file ownership metadata.
 * Created by: NinhDD - HE186113
 */
package com.group3.cinema.config;

import com.group3.cinema.service.CatalogService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CatalogInitializer {

    private final CatalogService catalogService;

    @PostConstruct
    public void initializeCatalogs() {
        catalogService.seedFromExistingRooms();
    }
}
