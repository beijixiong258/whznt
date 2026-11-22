package com.whznt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AdminRequests {

    private AdminRequests() {
    }

    public record UpdateUser(@NotBlank @Size(max = 30) String phone, @Size(max = 40) String password,
                             @NotNull Boolean enabled) {
    }

    public record Grant(@NotNull UUID requestId, @Positive int points, @NotBlank @Size(max = 200) String note) {
    }

    public record CreditOrder(@Positive int points, @Size(max = 200) String note) {
    }

    public record RejectOrder(@NotBlank @Size(max = 200) String note) {
    }

    public record Upstream(@NotBlank @Size(max = 512) String baseUrl, @NotBlank @Size(max = 128) String model,
                           @Size(max = 1024) String apiKey) {
    }
}
