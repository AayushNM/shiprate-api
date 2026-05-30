package com.aayushnair.shiprate_api.dto;

public class SurchargeResponse {
    public String shipmentId;
    public double baseRate;
    public double fuelSurcharge;
    public double remoteAreaFee;
    public double finalCharge;

    public SurchargeResponse(String shipmentId, double baseRate,
                             double fuelSurcharge, double remoteAreaFee,
                             double finalCharge) {
        this.shipmentId    = shipmentId;
        this.baseRate      = baseRate;
        this.fuelSurcharge = fuelSurcharge;
        this.remoteAreaFee = remoteAreaFee;
        this.finalCharge   = finalCharge;
    }
}
