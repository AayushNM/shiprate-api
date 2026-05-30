package com.aayushnair.shiprate_api.service;

import com.aayushnair.shiprate_api.dto.SurchargeRequest;
import com.aayushnair.shiprate_api.dto.SurchargeResponse;
import com.aayushnair.shiprate_api.entity.Shipment;
import com.aayushnair.shiprate_api.repository.ShipmentRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class SurchargeService {

    private static final double BASE_FLAT_RATE     = 10.0;
    private static final double RATE_PER_LB        = 1.5;
    private static final double EXPRESS_MULTIPLIER = 1.15;
    private static final double FUEL_SURCHARGE_PCT = 0.08;
    private static final double REMOTE_AREA_FEE    = 25.0;

    private final ShipmentRepository shipmentRepository;

    public SurchargeService(ShipmentRepository shipmentRepository) {
        this.shipmentRepository = shipmentRepository;
    }

    @Transactional
    public SurchargeResponse calculate(SurchargeRequest request) {
        double baseRate = BASE_FLAT_RATE + request.getWeight() * RATE_PER_LB;

        if (request.getServiceType().equalsIgnoreCase("EXPRESS")) {
            baseRate *= EXPRESS_MULTIPLIER;
        }

        double fuelSurcharge = baseRate * FUEL_SURCHARGE_PCT;

        double remoteAreaFee = 0;
        if (request.getDestination().equalsIgnoreCase("AK") ||
                request.getDestination().equalsIgnoreCase("HI")) {
            remoteAreaFee = REMOTE_AREA_FEE;
        }

        double finalCharge = baseRate + fuelSurcharge + remoteAreaFee;

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

        return new SurchargeResponse(
                saved.getId(),
                baseRate,
                fuelSurcharge,
                remoteAreaFee,
                finalCharge
        );
    }
}