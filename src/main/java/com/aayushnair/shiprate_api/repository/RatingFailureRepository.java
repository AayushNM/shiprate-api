package com.aayushnair.shiprate_api.repository;

import com.aayushnair.shiprate_api.entity.RatingFailure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RatingFailureRepository extends JpaRepository<RatingFailure, String> {

    // AI agent queries: "give me all OPEN failures to work on"
    List<RatingFailure> findByStatus(String status);

    // Dashboard query: failures for a specific shipment
    List<RatingFailure> findByShipmentId(String shipmentId);
}