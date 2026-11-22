package com.whznt.vo;

import com.whznt.entity.Account;
import com.whznt.entity.PointCredit;

import java.time.OffsetDateTime;
import java.util.List;

public final class AdminViews {

    private AdminViews() {
    }

    public record Page<T>(List<T> items, boolean hasMore) {
    }

    public record User(long id, String phone, boolean enabled, int points) {
        public static User from(Account account) {
            return new User(account.getId(), account.getPhone(), account.isEnabled(), account.getPoints());
        }
    }

    public record Credit(long id, long operatorId, Long rechargeOrderId, int points, int balanceAfter,
                         String note, OffsetDateTime createdAt) {
        public static Credit from(PointCredit row) {
            return new Credit(row.getId(), row.getOperatorId(), row.getRechargeOrderId(), row.getPoints(),
                    row.getBalanceAfter(), row.getNote(), row.getCreatedAt());
        }
    }

    public record Order(long ownerId, String phone, RechargeViews.Order order) {
    }

    public record Upstream(String baseUrl, String model, boolean hasKey) {
    }

    public record Probe(boolean connected, Long firstReplyMs, long totalMs, String message) {
    }
}
