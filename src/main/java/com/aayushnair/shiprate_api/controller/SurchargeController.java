package com.aayushnair.shiprate_api.controller;

import com.aayushnair.shiprate_api.dto.SurchargeRequest;
import com.aayushnair.shiprate_api.dto.SurchargeResponse;
import com.aayushnair.shiprate_api.entity.Shipment;
import com.aayushnair.shiprate_api.repository.ShipmentRepository;
import com.aayushnair.shiprate_api.service.SurchargeService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SurchargeController {

    private final SurchargeService surchargeService;
    private final ShipmentRepository shipmentRepository;

    public SurchargeController(SurchargeService surchargeService,
                               ShipmentRepository shipmentRepository) {
        this.surchargeService    = surchargeService;
        this.shipmentRepository  = shipmentRepository;
    }

    @GetMapping("/health")
    public String health() {
        return "ShipRate API is running";
    }

    @GetMapping("/rates")
    public List<String> rates() {
        return List.of("GROUND", "EXPRESS", "OVERNIGHT");
    }

    @PostMapping("/surcharge/calculate")
    public SurchargeResponse calculate(@Valid @RequestBody SurchargeRequest request) {
        return surchargeService.calculate(request);
    }

    @GetMapping("/shipments")
    public List<Shipment> shipments() {
        return shipmentRepository.findAll();
    }

    // Read-only rate lookup — result served from Redis cache after first call
    @GetMapping("/surcharge/lookup")
    public SurchargeResponse lookup(@Valid @ModelAttribute SurchargeRequest request) {
        return surchargeService.lookupRate(request);
    }
}