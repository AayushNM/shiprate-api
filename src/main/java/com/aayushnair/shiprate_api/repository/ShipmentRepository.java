package com.aayushnair.shiprate_api.repository;

import com.aayushnair.shiprate_api.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, String> {}
