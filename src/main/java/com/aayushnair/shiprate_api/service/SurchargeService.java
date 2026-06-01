package com.aayushnair.shiprate_api.service;

import com.aayushnair.shiprate_api.dto.SurchargeRequest;
import com.aayushnair.shiprate_api.dto.SurchargeResponse;
import com.aayushnair.shiprate_api.entity.Shipment;
import com.aayushnair.shiprate_api.repository.ShipmentRepository;
import jakarta.transaction.Transactional;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class SurchargeService {

    // Pricing constants — in production these would come from a
    // database or configuration service so they can be updated without redeployment
    private static final double BASE_FLAT_RATE     = 10.0;
    private static final double RATE_PER_LB        = 1.5;
    private static final double EXPRESS_MULTIPLIER = 1.15;
    private static final double FUEL_SURCHARGE_PCT = 0.08;
    private static final double REMOTE_AREA_FEE    = 25.0;

    private final ShipmentRepository shipmentRepository;

    public SurchargeService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    // @Cacheable intercepts the method call — checks Redis first before executing
    // Cache key is composite: origin-destination-weight-serviceType
    // Same inputs always produce the same output (pure function) — ideal for caching
    // shipmentId is null — this is a read-only rate check, no DB write
    @Cacheable(
            value = "surcharge-rates",
            key = "#request.origin + '-' + #request.destination + '-' + #request.weight + '-' + #request.serviceType"
    )
    public SurchargeResponse lookupRate(SurchargeRequest request) {
        double baseRate      = calculateBaseRate(request);
        double fuelSurcharge = baseRate * FUEL_SURCHARGE_PCT;
        double remoteAreaFee = isRemote(request.getDestination()) ? REMOTE_AREA_FEE : 0;
        return new SurchargeResponse(null, baseRate, fuelSurcharge,
                remoteAreaFee, baseRate + fuelSurcharge + remoteAreaFee);
    }

    // NOT cached — every call must persist a new shipment record to the database
    // Caching this would skip the DB write on cache hits — incorrect behavior
    @Transactional
    // @Transactional ensures the DB write either fully succeeds or fully rolls back
    // If save() fails partway through, no partial record is left in the database
    public SurchargeResponse calculate(SurchargeRequest request) {
        double baseRate      = calculateBaseRate(request);
        double fuelSurcharge = baseRate * FUEL_SURCHARGE_PCT;
        double remoteAreaFee = isRemote(request.getDestination()) ? REMOTE_AREA_FEE : 0;
        double finalCharge   = baseRate + fuelSurcharge + remoteAreaFee;

        Shipment shipment = new Shipment();
        shipment.setOrigin(request.getOrigin());
        shipment.setDestination(request.getDestination());
        shipment.setWeight(request.getWeight());
        shipment.setServiceType(request.getServiceType());
        shipment.setBaseRate(baseRate);
        shipment.setFuelSurcharge(fuelSurcharge);
        shipment.setRemoteAreaFee(remoteAreaFee);
        shipment.setFinalCharge(finalCharge);

        Shipment saved = shipmentRepository.save(shipment);
        return new SurchargeResponse(saved.getId(), baseRate,
                fuelSurcharge, remoteAreaFee, finalCharge);
    }

    // base = flat rate + (weight x rate per lb)
    // EXPRESS adds a 15% multiplier on top
    private double calculateBaseRate(SurchargeRequest request) {
        double base = BASE_FLAT_RATE + request.getWeight() * RATE_PER_LB;
        return request.getServiceType().equalsIgnoreCase("EXPRESS")
                ? base * EXPRESS_MULTIPLIER : base;
    }

    // Remote area surcharge applies to Alaska and Hawaii
    // These states have significantly higher delivery costs due to geography
    private boolean isRemote(String destination) {
        return destination.equalsIgnoreCase("AK") ||
                destination.equalsIgnoreCase("HI");
    }
}