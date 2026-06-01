package com.aayushnair.shiprate_api;

import com.aayushnair.shiprate_api.dto.SurchargeRequest;
import com.aayushnair.shiprate_api.dto.SurchargeResponse;
import com.aayushnair.shiprate_api.repository.ShipmentRepository;
import com.aayushnair.shiprate_api.service.SurchargeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.within;

// MockitoExtension wires up @Mock and @InjectMocks without starting
// a Spring context — tests run in milliseconds with no infrastructure
@ExtendWith(MockitoExtension.class)
class SurchargeServiceTest {

    // Creates a fake ShipmentRepository — no real database involved
    // You control exactly what it returns
    @Mock
    private ShipmentRepository shipmentRepository;

    // Creates a real SurchargeService and injects the mock repository into it
    @InjectMocks
    private SurchargeService surchargeService;

    @Test
    void calculate_groundRate_returnsCorrectCharges() {
        // When save() is called with any argument, return that same argument back
        // Simulates what a real JPA repository does — saves and returns the entity
        // Without this, save() returns null and the service throws NullPointerException
        when(shipmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SurchargeResponse response = surchargeService.calculate(
                buildRequest("CA", "NY", 10.0, "GROUND"));

        // base = 10 + (10 x 1.5) = 25.0
        // fuel = 25.0 x 0.08 = 2.0
        // NY is not remote, so remoteAreaFee = 0
        // total = 25.0 + 2.0 + 0 = 27.0
        assertThat(response.baseRate).isEqualTo(25.0);
        assertThat(response.fuelSurcharge).isEqualTo(2.0);
        assertThat(response.remoteAreaFee).isEqualTo(0.0);
        assertThat(response.finalCharge).isEqualTo(27.0);
    }

    @Test
    void calculate_expressService_appliesMultiplier() {
        when(shipmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SurchargeResponse response = surchargeService.calculate(
                buildRequest("CA", "NY", 10.0, "EXPRESS"));

        // Using isCloseTo instead of isEqualTo because double multiplication
        // introduces floating point precision errors (25.0 x 1.15 = 28.749999999999996)
        // offset(0.01) means "within 1 cent" — acceptable for monetary comparisons
        assertThat(response.baseRate).isCloseTo(28.75, within(0.01));
    }

    @Test
    void calculate_alaskaDestination_appliesRemoteAreaFee() {
        when(shipmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        SurchargeResponse response = surchargeService.calculate(
                buildRequest("CA", "AK", 5.0, "GROUND"));

        // AK (Alaska) is a remote destination — $25 flat fee applies
        assertThat(response.remoteAreaFee).isEqualTo(25.0);
    }

    @Test
    void calculate_persistsShipmentToDatabase() {
        when(shipmentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        surchargeService.calculate(buildRequest("TX", "FL", 8.0, "GROUND"));

        // Verify save() was actually called with the correct data
        // argThat creates a custom matcher — checks specific fields on the saved entity
        verify(shipmentRepository).save(argThat(s ->
                s.getOrigin().equals("TX") &&
                        s.getDestination().equals("FL") &&
                        s.getWeight() == 8.0
        ));
    }

    // Helper method — builds a SurchargeRequest without repeating boilerplate in every test
    private SurchargeRequest buildRequest(String origin, String dest,
                                          double weight, String serviceType) {
        SurchargeRequest req = new SurchargeRequest();
        req.setOrigin(origin);
        req.setDestination(dest);
        req.setWeight(weight);
        req.setServiceType(serviceType);
        return req;
    }
}