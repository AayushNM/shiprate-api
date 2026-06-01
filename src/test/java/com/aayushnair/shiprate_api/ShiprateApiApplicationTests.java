package com.aayushnair.shiprate_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test") // use application-test.yaml — disables Redis, uses H2
class ShiprateApiApplicationTests {

	@Test
	void contextLoads() {
		// Verifies the Spring application context starts without errors
	}
}