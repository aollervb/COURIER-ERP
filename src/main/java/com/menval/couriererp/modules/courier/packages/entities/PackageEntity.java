package com.menval.couriererp.modules.courier.packages.entities;

import com.menval.couriererp.modules.common.models.TenantScopedBaseModel;
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
                @UniqueConstraint(name = "uq_packages_tenant_carrier_tracking", columnNames = {"tenant_id", "carrier", "originalTrackingNumber"})
        }
)
@Data
public class PackageEntity extends TenantScopedBaseModel {
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

    /**
     * Create a new package in received state (no owner, RECEIVED_US_UNASSIGNED).
     * Use this instead of setters when receiving a package in the domain.
     */
    public static PackageEntity receive(Carrier carrier, String originalTrackingNumber, Instant receivedAt) {
        if (carrier == null) carrier = Carrier.UNKNOWN;
        if (originalTrackingNumber == null || originalTrackingNumber.isBlank()) {
            throw new IllegalArgumentException("Tracking number is required");
        }
        String tracking = originalTrackingNumber.trim();
        PackageEntity pkg = new PackageEntity();
        pkg.setCarrier(carrier);
        pkg.setOriginalTrackingNumber(tracking);
        pkg.setOwner(null);
        pkg.setStatus(PackageStatus.RECEIVED_US_UNASSIGNED);
        pkg.setReceivedAt(receivedAt);
        pkg.setLastSeenAt(receivedAt);
        return pkg;
    }

    public void markReceivedNow(Instant now) {
        if (this.receivedAt == null) this.receivedAt = now;
        this.lastSeenAt = now;
    }

    public boolean isAssigned() {
        return owner != null;
    }

    /**
     * Whether this package can be assigned to an account (received, not yet assigned).
     */
    public boolean canBeAssigned() {
        return !isAssigned() && status == PackageStatus.RECEIVED_US_UNASSIGNED;
    }

    /**
     * Assign this package to a customer account. Domain logic: validates account and package state,
     * sets owner and status RECEIVED_US_ASSIGNED.
     *
     * @param account the customer account to assign (must be active and not null)
     * @throws IllegalArgumentException if account is null, inactive, or package is already assigned
     */
    public void assignToAccount(AccountEntity account) {
        if (account == null) {
            throw new IllegalArgumentException("Account is required to assign a package");
        }
        if (!account.isActive()) {
            throw new IllegalArgumentException("Cannot assign package to inactive account: " + account.getCode());
        }
        if (isAssigned()) {
            throw new IllegalStateException("Package is already assigned to account: " + owner.getCode());
        }
        if (!canBeAssigned()) {
            throw new IllegalStateException("Package cannot be assigned: status is " + status);
        }
        this.owner = account;
        this.status = PackageStatus.RECEIVED_US_ASSIGNED;
        this.lastSeenAt = Instant.now();
    }
}
