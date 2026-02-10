package com.menval.couriererp.tenant.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TenantSettings {

    @Column(name = "feature_auto_assign")
    private boolean autoAssignEnabled = false;

    @Column(name = "feature_batching")
    private boolean batchingEnabled = true;

    @Column(name = "account_code_prefix", length = 10)
    private String accountCodePrefix = "CR";

    @Column(name = "account_code_length")
    private int accountCodeLength = 6;

    @Column(length = 50)
    private String timezone = "UTC";

    @Column(length = 3)
    private String currency = "USD";

    @Column(length = 5)
    private String locale = "en_US";

    @Column(name = "max_users")
    private int maxUsers = 10;

    @Column(name = "max_packages_per_month")
    private int maxPackagesPerMonth = 1000;
}
