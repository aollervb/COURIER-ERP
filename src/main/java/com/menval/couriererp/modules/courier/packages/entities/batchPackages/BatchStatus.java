package com.menval.couriererp.modules.courier.packages.entities.batchPackages;

public enum BatchStatus {
    DRAFT,          // created but not ready
    OPEN,           // packages can be added/removed
    CLOSED,         // frozen; ready for transport
    IN_TRANSIT,     // departed origin
    ARRIVED,        // arrived destination hub
    COMPLETED,      // processed at destination
    CANCELLED
}

