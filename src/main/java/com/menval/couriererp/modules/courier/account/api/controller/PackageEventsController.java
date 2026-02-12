package com.menval.couriererp.modules.courier.packages.controllers.api;

import com.menval.couriererp.modules.courier.packages.dto.PackageEventDto;
import com.menval.couriererp.modules.courier.packages.services.PackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * API endpoints used by the receiving page (e.g. package events modal). Kept separate from MVC controllers.
 */
@RestController
@RequestMapping("/packages/receiving")
@RequiredArgsConstructor
public class PackageEventsController {

    private final PackageService packageService;

    @GetMapping("/{packageId}/events")
    public List<PackageEventDto> getPackageEvents(@PathVariable Long packageId) {
        return packageService.getEventsForPackage(packageId);
    }
}
