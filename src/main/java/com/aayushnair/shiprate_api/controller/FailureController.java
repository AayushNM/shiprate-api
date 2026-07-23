package com.aayushnair.shiprate_api.controller;

import com.aayushnair.shiprate_api.entity.RatingFailure;
import com.aayushnair.shiprate_api.repository.RatingFailureRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/failures")
public class FailureController {

    private final RatingFailureRepository failureRepository;

    public FailureController(RatingFailureRepository failureRepository) {
        this.failureRepository = failureRepository;
    }

    // Python AI agent calls this to get failures to work on
    // ?status=OPEN is the most common query
    @GetMapping
    public List<RatingFailure> list(
            @RequestParam(required = false) String status) {
        if (status != null) {
            return failureRepository.findByStatus(status);
        }
        return failureRepository.findAll();
    }

    @GetMapping("/{id}")
    public RatingFailure get(@PathVariable String id) {
        return failureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Failure not found: " + id));
    }

    // Log a new failure — called when a surcharge calculation goes wrong
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RatingFailure create(@RequestBody RatingFailure failure) {
        failure.setStatus("OPEN");
        return failureRepository.save(failure);
    }

    // AI agent writes its findings back here
    // PATCH is correct — partial update, not a full replace
    @PatchMapping("/{id}/status")
    public RatingFailure updateStatus(
            @PathVariable String id,
            @RequestParam String status,
            @RequestParam(required = false) String remediationNotes,
            @RequestParam(required = false) Double triageScore) {

        RatingFailure failure = failureRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Failure not found: " + id));

        failure.setStatus(status);

        if (remediationNotes != null) failure.setRemediationNotes(remediationNotes);
        if (triageScore     != null) failure.setTriageScore(triageScore);

        // Stamp the resolution time when we reach a terminal state
        if (status.equals("REMEDIATED") || status.equals("CLOSED")) {
            failure.setResolvedAt(LocalDateTime.now());
        }

        return failureRepository.save(failure);
    }
}