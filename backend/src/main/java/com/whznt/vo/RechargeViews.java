package com.whznt.vo;

import com.whznt.entity.RechargeOrder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class RechargeViews {

    private RechargeViews() {
    }

    public record Settings(boolean qrAvailable, List<Integer> presets) {
    }

    public record Order(long id, String number, BigDecimal amount, String note, OffsetDateTime submittedAt) {
        public static Order from(RechargeOrder order) {
            return new Order(order.getId(), order.getRequestNumber(), order.getAmount(),
                    order.getNote(), order.getCreatedAt());
        }
    }
}
