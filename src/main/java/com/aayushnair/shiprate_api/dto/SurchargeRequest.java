package com.aayushnair.shiprate_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SurchargeRequest {

    @NotBlank(message = "Origin is required")
    private String origin;

    @NotBlank(message = "Destination is required")
    private String destination;

    @Positive(message = "Weight must be greater than zero")
    private double weight;

    @NotBlank(message = "Service type is required")
    private String serviceType;
}