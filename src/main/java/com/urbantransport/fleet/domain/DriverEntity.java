package com.urbantransport.fleet.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "drivers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "license_number", nullable = false, length = 30)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "license_type", nullable = false, length = 2)
    private LicenseType licenseType;

    @Column(name = "license_expires_on", nullable = false)
    private LocalDate licenseExpiresOn;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "base_salary_per_trip", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseSalaryPerTrip;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
