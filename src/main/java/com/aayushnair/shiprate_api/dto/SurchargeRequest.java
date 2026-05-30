package com.aayushnair.shiprate_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class SurchargeRequest {
    @NotBlank private String origin;
    @NotBlank private String destination;
    @Positive  private double weight;
    @NotBlank  private String serviceType;
}
