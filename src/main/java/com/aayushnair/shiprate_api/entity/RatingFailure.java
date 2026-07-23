package com.aayushnair.shiprate_api.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Data
@Table(name = "rating_failures",
        indexes = {
                @Index(name = "idx_failure_status",   columnList = "status"),
                @Index(name = "idx_failure_created",  columnList = "createdAt")
        })
public class RatingFailure {

    @Id
    private String id = UUID.randomUUID().toString();

    // Which shipment triggered this failure — null if failure happened before save
    private String shipmentId;

    // Coarse category — AI agent uses this to route to the right remediation logic
    // e.g. RATE_NOT_FOUND, INVALID_DESTINATION, SURCHARGE_MISMATCH, UNKNOWN
    private String failureType;

    // The actual error message from the system — raw, unprocessed
    @Column(columnDefinition = "TEXT")
    private String failureMessage;

    // The original request payload that caused the failure, stored as JSON string
    // AI agent needs this to understand what was being attempted
    @Column(columnDefinition = "TEXT")
    private String rawPayload;

    // Lifecycle: OPEN → TRIAGED → SUGGESTED / ESCALATED / REMEDIATED → CLOSED
    // AI agent reads OPEN failures, writes TRIAGED/ SUGGESTED / ESCALATED / REMEDIATED status back
    private String status = "OPEN";

    // What the AI agent decided to do — written by the Remediation Agent
    @Column(columnDefinition = "TEXT")
    private String remediationNotes;

    // Match score from 0.0 to 1.0 — how confident the Triage Agent was
    private Double triageScore;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}