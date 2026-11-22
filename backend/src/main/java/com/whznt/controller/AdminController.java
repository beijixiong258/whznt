package com.whznt.controller;

import com.whznt.config.AccountPrincipal;
import com.whznt.dto.AdminRequests;
import com.whznt.dto.WorkspaceRequests;
import com.whznt.entity.RechargeOrder;
import com.whznt.exception.WorkspaceException;
import com.whznt.integration.TextModel;
import com.whznt.service.AccountService;
import com.whznt.service.RechargeService;
import com.whznt.vo.AdminViews;
import com.whznt.vo.RechargeViews;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AccountService accounts;
    private final RechargeService recharge;
    private final TextModel model;

    @GetMapping("/upstream")
    public AdminViews.Upstream upstream() {
        return model.settings();
    }

    @PutMapping("/upstream")
    public AdminViews.Upstream saveUpstream(@Valid @RequestBody AdminRequests.Upstream input) {
        return model.save(input);
    }

    @PostMapping("/upstream/test")
    public AdminViews.Probe testUpstream(@Valid @RequestBody AdminRequests.Upstream input) {
        return model.probe(input);
    }

    @GetMapping("/users")
    public AdminViews.Page<AdminViews.User> users(@RequestParam(defaultValue = "") @Pattern(regexp = "[0-9]{0,11}") String phone,
                                                  @RequestParam(defaultValue = "1") @Positive int page) {
        return accounts.users(phone, page);
    }

    @PostMapping("/users")
    public AdminViews.User createUser(@Valid @RequestBody WorkspaceRequests.Register input) {
        return accounts.createUser(input.phone(), input.password());
    }

    @PatchMapping("/users/{id}")
    public AdminViews.User updateUser(@PathVariable long id, @Valid @RequestBody AdminRequests.UpdateUser input) {
        return accounts.updateUser(id, input);
    }

    @DeleteMapping("/users/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(@PathVariable long id) {
        accounts.deleteUser(id);
    }

    @GetMapping("/users/{id}/credits")
    public List<AdminViews.Credit> credits(@PathVariable long id) {
        return accounts.credits(id);
    }

    @PostMapping("/users/{id}/credits")
    public AdminViews.Credit grant(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                  @Valid @RequestBody AdminRequests.Grant input) {
        return accounts.addPoints(id, principal.accountId(), input.requestId(), null, input.points(), input.note().strip());
    }

    @GetMapping("/recharges")
    public AdminViews.Page<AdminViews.Order> recharges(@RequestParam(required = false) RechargeOrder.State state,
                                                      @RequestParam(defaultValue = "1") @Positive int page) {
        return recharge.reviewQueue(state, page);
    }

    @GetMapping("/recharges/qr")
    public ResponseEntity<Resource> rechargeQr() {
        return RechargeController.image(recharge.qrImage());
    }

    @PostMapping(value = "/recharges/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveRechargeQr(@RequestParam MultiValueMap<String, MultipartFile> uploads) {
        List<MultipartFile> images = uploads.get("file");
        if (uploads.size() != 1 || images == null || images.size() != 1) {
            throw WorkspaceException.badRequest("每次只能上传一张收款二维码。");
        }
        recharge.saveQr(images.get(0));
    }

    @GetMapping("/recharges/{id}/receipt")
    public ResponseEntity<Resource> receipt(@PathVariable long id) {
        return RechargeController.image(recharge.reviewReceipt(id));
    }

    @PostMapping("/recharges/{id}/credit")
    public RechargeViews.Order credit(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                      @Valid @RequestBody AdminRequests.CreditOrder input) {
        return recharge.credit(principal.accountId(), id, input.points(), input.note());
    }

    @PostMapping("/recharges/{id}/reject")
    public RechargeViews.Order reject(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable long id,
                                      @Valid @RequestBody AdminRequests.RejectOrder input) {
        return recharge.reject(principal.accountId(), id, input.note());
    }
}
