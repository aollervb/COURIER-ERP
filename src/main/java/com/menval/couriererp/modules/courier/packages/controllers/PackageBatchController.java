package com.menval.couriererp.modules.courier.packages.controllers;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.modules.courier.packages.entities.PackageEntity;
import com.menval.couriererp.modules.courier.packages.entities.batchPackages.BatchStatus;
import com.menval.couriererp.modules.courier.packages.entities.batchPackages.PackageBatchEntity;
import com.menval.couriererp.modules.courier.packages.entities.batchPackages.TransportMode;
import com.menval.couriererp.modules.courier.packages.repositories.PackageRepository;
import com.menval.couriererp.modules.courier.packages.services.PackageBatchService;
import com.menval.couriererp.modules.courier.packages.services.PackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.List;

@Controller
@RequestMapping("/packages/batches")
@RequiredArgsConstructor
public class PackageBatchController {

    private final PackageBatchService batchService;
    private final PackageService packageService;
    private final PackageRepository packageRepository;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("batches", batchService.listRecent());
        return "packages/batches/list";
    }

    @GetMapping("/new")
    public String newBatchForm(Model model) {
        model.addAttribute("transportModes", TransportMode.values());
        return "packages/batches/new";
    }

    @PostMapping("/new")
    public String createBatch(@RequestParam("referenceCode") String referenceCode,
                             @RequestParam("transportMode") TransportMode transportMode,
                             @RequestParam("originFacilityCode") String originFacilityCode,
                             @RequestParam("destinationFacilityCode") String destinationFacilityCode,
                             @RequestParam("destinationCountry") String destinationCountry,
                             @RequestParam(value = "plannedDepartureAt", required = false) String plannedDepartureAtStr,
                             @RequestParam(value = "containerType", required = false) String containerType,
                             @org.springframework.security.core.annotation.AuthenticationPrincipal BaseUser user,
                             RedirectAttributes redirectAttributes) {
        try {
            Instant plannedDeparture = null;
            if (plannedDepartureAtStr != null && !plannedDepartureAtStr.isBlank()) {
                try {
                    plannedDeparture = Instant.parse(plannedDepartureAtStr);
                } catch (Exception e1) {
                    try {
                        plannedDeparture = java.time.LocalDateTime.parse(plannedDepartureAtStr)
                                .atZone(java.time.ZoneId.systemDefault()).toInstant();
                    } catch (Exception ignored) {
                    }
                }
            }
            PackageBatchEntity batch = batchService.createBatch(
                    referenceCode, transportMode, originFacilityCode, destinationFacilityCode,
                    destinationCountry, plannedDeparture, containerType, user);
            redirectAttributes.addFlashAttribute("message", "Batch " + batch.getReferenceCode() + " created.");
            return "redirect:/packages/batches/" + batch.getId();
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/packages/batches/new";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        return batchService.findById(id)
                .map(batch -> {
                    List<PackageEntity> packages = packageRepository.findByBatch_Id(id);
                    model.addAttribute("batch", batch);
                    model.addAttribute("packages", packages);
                    model.addAttribute("canAddRemove", batch.isOpenForChanges());
                    model.addAttribute("canSeal", batch.isOpenForChanges());
                    model.addAttribute("canMarkInTransit", batch.getStatus() == BatchStatus.CLOSED);
                    model.addAttribute("canReceiveAtDestination", batch.getStatus() == BatchStatus.IN_TRANSIT);
                    return "packages/batches/detail";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Batch not found.");
                    return "redirect:/packages/batches";
                });
    }

    @GetMapping("/{id}/add-packages")
    public String addPackagesForm(@PathVariable Long id,
                                 @RequestParam(name = "page", defaultValue = "0") int page,
                                 @RequestParam(name = "size", defaultValue = "20") int size,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {
        return batchService.findById(id)
                .filter(PackageBatchEntity::isOpenForChanges)
                .map(batch -> {
                    model.addAttribute("batch", batch);
                    Page<PackageEntity> assignable = packageService.findAssignableForBatch(PageRequest.of(page, size));
                    model.addAttribute("assignablePackages", assignable);
                    return "packages/batches/add-packages";
                })
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("error", "Batch not found or closed.");
                    return "redirect:/packages/batches";
                });
    }

    @PostMapping("/{id}/packages")
    public String addPackages(@PathVariable Long id,
                             @RequestParam("packageIds") List<Long> packageIds,
                             RedirectAttributes redirectAttributes) {
        if (packageIds == null || packageIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Select at least one package.");
            return "redirect:/packages/batches/" + id + "/add-packages";
        }
        try {
            for (Long packageId : packageIds) {
                batchService.addPackageToBatch(id, packageId);
            }
            redirectAttributes.addFlashAttribute("message", "Added " + packageIds.size() + " package(s) to batch.");
            return "redirect:/packages/batches/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/packages/batches/" + id + "/add-packages";
        }
    }

    @PostMapping("/{id}/packages/{packageId}/remove")
    public String removePackage(@PathVariable Long id,
                               @PathVariable Long packageId,
                               RedirectAttributes redirectAttributes) {
        try {
            batchService.removePackageFromBatch(id, packageId);
            redirectAttributes.addFlashAttribute("message", "Package removed from batch.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/packages/batches/" + id;
    }

    @PostMapping("/{id}/seal")
    public String sealBatch(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            batchService.sealBatch(id);
            redirectAttributes.addFlashAttribute("message", "Batch sealed.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/packages/batches/" + id;
    }

    @PostMapping("/{id}/in-transit")
    public String markInTransit(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            batchService.markBatchInTransit(id);
            redirectAttributes.addFlashAttribute("message", "Batch marked in transit.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/packages/batches/" + id;
    }

    @PostMapping("/{id}/arrive")
    public String markArrived(@PathVariable Long id,
                             @RequestParam(value = "facilityCode", required = false) String facilityCode,
                             @org.springframework.security.core.annotation.AuthenticationPrincipal BaseUser user,
                             RedirectAttributes redirectAttributes) {
        try {
            batchService.markBatchArrived(id, facilityCode, user);
            redirectAttributes.addFlashAttribute("message", "Batch received at destination.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/packages/batches/" + id;
    }
}
