package com.menval.couriererp.modules.courier.packages.entities;

public enum PackageStatus {
    RECEIVED_US_UNASSIGNED,
    RECEIVED_US_ASSIGNED,
    PROCESSING_US,
    READY_TO_EXPORT,
    IN_TRANSIT_INTERNATIONAL,
    ARRIVED_DR,
    CUSTOMS_HOLD,
    OUT_FOR_DELIVERY,
    READY_FOR_PICKUP,
    DELIVERED,
    EXCEPTION
}
