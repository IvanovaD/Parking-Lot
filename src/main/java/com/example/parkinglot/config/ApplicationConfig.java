package com.example.parkinglot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ApplicationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI parkingLotOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Parking Lot API")
                .version("v1")
                .description("Reserve and cancel one-hour parking spaces."));
    }
}
