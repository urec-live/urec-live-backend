package com.ureclive.urec_live_backend.dto;

import jakarta.validation.constraints.NotNull;

public class SetOutOfOrderRequest {

    @NotNull(message = "outOfOrder is required")
    private Boolean outOfOrder;

    public Boolean getOutOfOrder() { return outOfOrder; }
    public void setOutOfOrder(Boolean outOfOrder) { this.outOfOrder = outOfOrder; }
}
