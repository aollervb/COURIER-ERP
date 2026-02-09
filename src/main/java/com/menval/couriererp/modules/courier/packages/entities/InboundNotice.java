package com.menval.couriererp.modules.courier.packages.entities;

import com.menval.couriererp.modules.common.models.BaseModel;
import com.menval.couriererp.modules.courier.account.entities.AccountEntity;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(
        name = "inbound_notices",
        indexes = {
                @Index(name = "idx_inbound_tracking", columnList = "carrier,originalTrackingNumber"),
                @Index(name = "idx_inbound_account", columnList = "account_id")
        }
)
@Data
public class InboundNotice extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Carrier carrier = Carrier.UNKNOWN;

    @Column(nullable = false, length = 64)
    private String originalTrackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InboundNoticeStatus status = InboundNoticeStatus.OPEN;

    // Optional fields to help later
    private String store;
    private String contentsDescription;
}
