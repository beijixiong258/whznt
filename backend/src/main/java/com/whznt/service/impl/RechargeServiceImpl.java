package com.whznt.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.whznt.dto.RechargeRequests;
import com.whznt.entity.RechargeOrder;
import com.whznt.exception.WorkspaceException;
import com.whznt.integration.PaymentImages;
import com.whznt.integration.PaymentImages.ImageView;
import com.whznt.integration.PrivateFiles;
import com.whznt.mapper.AccountMapper;
import com.whznt.mapper.RechargeOrderMapper;
import com.whznt.service.RechargeService;
import com.whznt.vo.RechargeViews;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RechargeServiceImpl extends ServiceImpl<RechargeOrderMapper, RechargeOrder> implements RechargeService {

    private static final List<Integer> PRESETS = List.of(200, 500, 1000);
    private final PaymentImages images;
    private final PrivateFiles files;
    private final AccountMapper accounts;

    @Override
    public RechargeViews.Settings settings() {
        return new RechargeViews.Settings(images.qrAvailable(), PRESETS);
    }

    @Override
    public ImageView qrImage() {
        return images.requireQr();
    }

    @Override
    public List<RechargeViews.Order> orders(long ownerId) {
        return lambdaQuery().eq(RechargeOrder::getOwnerId, ownerId).orderByDesc(RechargeOrder::getId)
                .list().stream().map(RechargeViews.Order::from).toList();
    }

    @Override
    @Transactional
    public RechargeViews.Order submit(long ownerId, RechargeRequests.Submit request) {
        String requestKey = request.requestId().toString();
        if (accounts.lockId(ownerId) == null) {
            throw WorkspaceException.notFound("找不到该账号。");
        }
        RechargeOrder priorRequest = lambdaQuery().eq(RechargeOrder::getOwnerId, ownerId)
                .eq(RechargeOrder::getRequestKey, requestKey).one();
        if (priorRequest != null) return RechargeViews.Order.from(priorRequest);
        PaymentImages.Receipt receipt = images.readReceipt(request.receipt());
        RechargeOrder priorReceipt = lambdaQuery().eq(RechargeOrder::getOwnerId, ownerId)
                .eq(RechargeOrder::getReceiptSha256, receipt.sha256()).one();
        if (priorReceipt != null) return RechargeViews.Order.from(priorReceipt);
        if (!images.qrAvailable()) {
            throw WorkspaceException.unavailable("收款二维码尚未配置，请稍后再提交。");
        }
        RechargeOrder order = new RechargeOrder();
        order.setOwnerId(ownerId);
        order.setRequestKey(requestKey);
        order.setRequestNumber("RC-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        order.setAmount(request.amount());
        order.setNote(Objects.toString(request.note(), "").strip());
        order.setReceiptKey(files.saveOriginal(receipt.bytes()));
        order.setReceiptSha256(receipt.sha256());
        order.setReceiptType(receipt.type().toString());
        order.setCreatedAt(OffsetDateTime.now());
        save(order);
        return RechargeViews.Order.from(order);
    }

    @Override
    public ImageView receipt(long ownerId, long id) {
        RechargeOrder order = lambdaQuery().eq(RechargeOrder::getOwnerId, ownerId).eq(RechargeOrder::getId, id).one();
        if (order == null) {
            throw WorkspaceException.notFound("找不到该充值申请。");
        }
        return new ImageView(files.download(order.getReceiptKey()), MediaType.valueOf(order.getReceiptType()));
    }
}
