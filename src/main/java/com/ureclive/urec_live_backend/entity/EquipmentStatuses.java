package com.ureclive.urec_live_backend.entity;

/**
 * Values of {@link Equipment#getStatus()}. The column is free-form text; these are the values the
 * code relies on. Compare them case-insensitively.
 */
public final class EquipmentStatuses {

    public static final String AVAILABLE = "Available";
    public static final String IN_USE = "In Use";
    /** Set only by admins. Blocks check-ins until an admin clears it or the last open report is resolved. */
    public static final String OUT_OF_ORDER = "Out of Order";

    private EquipmentStatuses() {}

    public static boolean isOutOfOrder(String status) {
        return status != null && OUT_OF_ORDER.equalsIgnoreCase(status.trim());
    }
}
