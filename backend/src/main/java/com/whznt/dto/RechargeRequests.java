package com.whznt.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.UUID;

public final class RechargeRequests {

    private RechargeRequests() {
    }

    public record Submit(
            @NotNull UUID requestId,
            @NotNull @DecimalMin("1") @DecimalMax("1000000") @Digits(integer = 7, fraction = 2) BigDecimal amount,
            @Size(max = 300) String note,
            @NotNull MultipartFile receipt) {
    }
}
