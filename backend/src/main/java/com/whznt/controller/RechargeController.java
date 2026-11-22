package com.whznt.controller;

import com.whznt.config.AccountPrincipal;
import com.whznt.dto.RechargeRequests;
import com.whznt.integration.PaymentImages.ImageView;
import com.whznt.service.RechargeService;
import com.whznt.vo.RechargeViews;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/recharge")
@RequiredArgsConstructor
public class RechargeController {

    private final RechargeService recharge;

    @GetMapping("/settings")
    public RechargeViews.Settings settings(@AuthenticationPrincipal AccountPrincipal account) {
        return recharge.settings(account.accountId());
    }

    @GetMapping("/qr")
    public ResponseEntity<Resource> qr() {
        return image(recharge.qrImage());
    }

    @GetMapping("/orders")
    public List<RechargeViews.Order> orders(@AuthenticationPrincipal AccountPrincipal account) {
        return recharge.orders(account.accountId());
    }

    @PostMapping(value = "/orders", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public RechargeViews.Order submit(@AuthenticationPrincipal AccountPrincipal account,
                                      @Valid @ModelAttribute RechargeRequests.Submit request) {
        return recharge.submit(account.accountId(), request);
    }

    @GetMapping("/orders/{id}/receipt")
    public ResponseEntity<Resource> receipt(@AuthenticationPrincipal AccountPrincipal account, @PathVariable long id) {
        return image(recharge.receipt(account.accountId(), id));
    }

    static ResponseEntity<Resource> image(ImageView view) {
        return ResponseEntity.ok().contentType(view.type()).cacheControl(CacheControl.noStore()).body(view.resource());
    }
}
