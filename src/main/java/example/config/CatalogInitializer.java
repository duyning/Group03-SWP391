package example.config;

import example.service.CatalogService;
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
