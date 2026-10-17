package com.urbantransport.fleet.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic fleetEventsTopic(FleetKafkaProperties properties) {
        return TopicBuilder.name(properties.fleetEvents()).partitions(3).replicas(1).build();
    }
}
