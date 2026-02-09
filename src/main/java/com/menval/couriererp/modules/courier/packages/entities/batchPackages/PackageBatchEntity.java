package com.menval.couriererp.modules.courier.packages.entities.batchPackages;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.modules.common.models.BaseModel;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(
        name = "package_batches",
        uniqueConstraints = {
                // reference code unique within tenant
                @UniqueConstraint(name = "uq_batch_tenant_ref", columnNames = {"tenant_id", "reference_code"})
        },
        indexes = {
                @Index(name = "idx_batch_tenant_status", columnList = "tenant_id,status"),
                @Index(name = "idx_batch_tenant_mode", columnList = "tenant_id,transport_mode"),
                @Index(name = "idx_batch_departure", columnList = "tenant_id,planned_departure_at"),
                @Index(name = "idx_batch_container_code", columnList = "tenant_id,reference_code")
        }
)
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class PackageBatchEntity extends BaseModel {

    @Column(name = "reference_code", nullable = false, length = 40, updatable = false)
    private String referenceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false, length = 16)
    private TransportMode transportMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private BatchStatus status = BatchStatus.DRAFT;

    @Column(name = "origin_facility_code", nullable = false, length = 32)
    private String originFacilityCode;

    @Column(name = "destination_facility_code", nullable = false, length = 32)
    private String destinationFacilityCode;

    @Column(name = "destination_country", nullable = false, length = 2)
    private String destinationCountry; // ISO-3166-1 alpha-2 like "DO"

    @Column(name = "planned_departure_at")
    private Instant plannedDepartureAt;

    @Column(name = "actual_departure_at")
    private Instant actualDepartureAt;

    @Column(name = "planned_arrival_at")
    private Instant plannedArrivalAt;

    @Column(name = "actual_arrival_at")
    private Instant actualArrivalAt;

    // Container fields (optional)
    @Column(name = "container_type", length = 24)
    private String containerType; // "BAG", "PALLET", "ULD", "CONTAINER", etc.

    // Denormalized totals (optional but useful)
    @Column(name = "package_count", nullable = true)
    private int packageCount = 0;

    @Column(name = "total_weight_grams", nullable = true)
    private long totalWeightGrams = 0;

    @Column(name = "total_volume_cm3", nullable = true)
    private long totalVolumeCm3 = 0;

    // audit links (unidirectional)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private BaseUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private BaseUser closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    public boolean isOpenForChanges() {
        return status == BatchStatus.DRAFT || status == BatchStatus.OPEN;
    }
}
