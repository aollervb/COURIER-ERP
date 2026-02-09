package com.menval.couriererp.modules.courier.packages.entities;

import com.menval.couriererp.auth.models.BaseUser;
import com.menval.couriererp.modules.common.models.BaseModel;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(
        name = "package_events",
        indexes = {
                @Index(name = "idx_pkg_events_pkg_time", columnList = "packageId,eventTime")
        }
)
@Data
public class PackageEventEntity extends BaseModel {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false)
    private PackageEntity pkg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PackageEventType type;

    @Column(nullable = false)
    private Instant eventTime;

    @Column(nullable = true, length = 32)
    private String facilityCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private BaseUser actor;

    @Column(nullable = true, length = 512)
    private String notes;

    public static PackageEventEntity of(PackageEntity pkg, PackageEventType type, Instant time, String facilityCode, BaseUser actor, String notes) {
        PackageEventEntity e = new PackageEventEntity();
        e.pkg = pkg;
        e.type = type;
        e.eventTime = time;
        e.facilityCode = facilityCode;
        e.actor = actor;
        e.notes = notes;
        return e;
    }
}
