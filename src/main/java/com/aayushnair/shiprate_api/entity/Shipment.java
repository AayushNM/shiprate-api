package com.aayushnair.shiprate_api.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "shipments",
       indexes = @Index(name = "idx_origin_destination",
                        columnList = "origin, destination"))
public class Shipment {

    @Id
    private String id = UUID.randomUUID().toString();

    private String origin;
    private String destination;
    private double weight;
    private String serviceType;
    private double baseRate;
    private double fuelSurcharge;
    private double remoteAreaFee;
    private double finalCharge;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
