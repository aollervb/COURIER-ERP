package com.menval.couriererp.modules.courier.packages.entities;

import com.menval.couriererp.modules.common.models.BaseModel;
import com.menval.couriererp.modules.courier.account.entities.AccountEntity;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(
        name = "packages",
        indexes = {
                @Index(name = "idx_packages_tracking", columnList = "carrier,originalTrackingNumber"),
                @Index(name = "idx_packages_owner_account", columnList = "owner_account_id"),
                @Index(name = "idx_packages_status", columnList = "status")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_packages_carrier_tracking", columnNames = {"carrier", "originalTrackingNumber"})
        }
)
@Data
public class PackageEntity extends BaseModel {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Carrier carrier = Carrier.UNKNOWN;

    @Column(nullable = false, length = 64)
    // TODO: OCR Capable Scanner ?
    private String originalTrackingNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_account_id") // nullable by default; customer who owns the package
    private AccountEntity owner;

    // Nullable until assigned
    @Column(nullable = true, length = 32)
    private String internalCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PackageStatus status = PackageStatus.RECEIVED_US_UNASSIGNED;

    @Column(nullable = false, updatable = false)
    private Instant receivedAt;

    @Column(nullable = false)
    private Instant lastSeenAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inbound_notice_id")
    private InboundNotice inboundNotice;

    private int weightGrams;
    private int lengthCm;
    private int widthCm;
    private int heightCm;

    /*
     Optional: link to a receiving batch/cart
     @Column(nullable = true, length = 32)
     private String receivingBatchCode;
     */

    public void markReceivedNow(Instant now) {
        if (this.receivedAt == null) this.receivedAt = now;
        this.lastSeenAt = now;
    }

    public boolean isAssigned() {
        return owner != null;
    }
}
