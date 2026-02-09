package com.menval.couriererp.modules.courier.packages.controllers;

import com.menval.couriererp.modules.courier.packages.entities.Carrier;
import com.menval.couriererp.modules.courier.packages.entities.PackageEntity;
import com.menval.couriererp.modules.courier.packages.entities.PackageStatus;
import com.menval.couriererp.modules.courier.packages.services.BatchReceiveResult;
import com.menval.couriererp.modules.courier.packages.services.PackageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * ERP view for warehouse: receive packages by scanning or manually entering tracking number.
 */
@Controller
@RequestMapping("/packages/receiving")
@RequiredArgsConstructor
public class PackageReceivingController {

    private final PackageService packageService;

    @GetMapping
    public String receivingForm(Model model,
                                 @RequestParam(name = "page", defaultValue = "0") int page,
                                 @RequestParam(name = "size", defaultValue = "20") int size) {
        model.addAttribute("carriers", carriersForSelect());
        Page<PackageEntity> unassigned = packageService.findByStatus(
                PackageStatus.RECEIVED_US_UNASSIGNED,
                PageRequest.of(page, size)
        );
        model.addAttribute("packages", unassigned);
        return "packages/receiving";
    }

    @PostMapping
    public String receivePackages(@RequestParam("carrier") Carrier carrier,
                                  @RequestParam("trackingNumbers") String trackingNumbersRaw,
                                  RedirectAttributes redirectAttributes) {
        List<String> lines = parseTrackingLines(trackingNumbersRaw);
        if (lines.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Enter at least one tracking number (one per line).");
            return "redirect:/packages/receiving";
        }
        BatchReceiveResult result = packageService.receivePackages(
                carrier != null ? carrier : Carrier.UNKNOWN,
                lines
        );
        redirectAttributes.addFlashAttribute("batchResult", result);
        if (result.receivedCount() > 0) {
            redirectAttributes.addFlashAttribute("message",
                    String.format("Received %d new package(s). %d duplicate(s) skipped.",
                            result.receivedCount(), result.duplicateCount()));
        } else if (result.duplicateCount() > 0) {
            redirectAttributes.addFlashAttribute("message",
                    "All " + result.duplicateCount() + " package(s) were already received (duplicates).");
        }
        if (!result.invalidLines().isEmpty()) {
            redirectAttributes.addFlashAttribute("error",
                    result.invalidLines().size() + " invalid line(s): " + String.join(", ", result.invalidLines()));
        }
        return "redirect:/packages/receiving";
    }

    private static List<String> parseTrackingLines(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Stream.of(raw.split("[\\r\\n]+"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    private static List<Carrier> carriersForSelect() {
        return Arrays.stream(Carrier.values()).filter(c -> c != Carrier.UNKNOWN).toList();
    }
}
