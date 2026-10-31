package com.urbantransport.fleet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka.topic")
public record FleetKafkaProperties(String fleetEvents, String identityEvents, String tripEvents) {
}
