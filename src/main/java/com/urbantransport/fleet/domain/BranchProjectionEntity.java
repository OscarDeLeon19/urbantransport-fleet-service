package com.urbantransport.fleet.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branches_projection")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BranchProjectionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 20)
    private String code;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
