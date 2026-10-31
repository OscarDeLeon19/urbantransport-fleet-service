package com.urbantransport.fleet.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "license_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LicenseAlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "driver_id", nullable = false)
    private UUID driverId;

    @Column(name = "days_before", nullable = false)
    private Short daysBefore;

    @Column(name = "expires_on", nullable = false)
    private LocalDate expiresOn;

    @Column(name = "notify_on", nullable = false)
    private LocalDate notifyOn;

    @Column(name = "sent_at")
    private Instant sentAt;
}
