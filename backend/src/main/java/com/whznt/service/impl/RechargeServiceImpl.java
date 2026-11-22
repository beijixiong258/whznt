package com.whznt.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.whznt.dto.RechargeRequests;
import com.whznt.entity.Account;
import com.whznt.entity.RechargeOrder;
import com.whznt.exception.WorkspaceException;
import com.whznt.integration.PaymentImages;
import com.whznt.integration.PaymentImages.ImageView;
import com.whznt.integration.PrivateFiles;
import com.whznt.mapper.AccountMapper;
import com.whznt.mapper.RechargeOrderMapper;
import com.whznt.service.AccountService;
import com.whznt.service.RechargeService;
import com.whznt.vo.AdminViews;
import com.whznt.vo.RechargeViews;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RechargeServiceImpl extends ServiceImpl<RechargeOrderMapper, RechargeOrder> implements RechargeService {

    private static final List<Integer> PRESETS = List.of(200, 500, 1000);
    private static final int PAGE_SIZE = 50;
    private final PaymentImages images;
    private final PrivateFiles files;
    private final AccountMapper accounts;
    private final AccountService accountService;

    @Override
    public RechargeViews.Settings settings(long ownerId) {
        return new RechargeViews.Settings(images.qrAvailable(), PRESETS, accounts.selectById(ownerId).getPoints());
    }

    @Override
    public ImageView qrImage() {
        return images.requireQr();
    }

    @Override
    public void saveQr(MultipartFile file) {
        images.saveQr(file);
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
        if (accounts.lock(ownerId) == null) {
            throw WorkspaceException.notFound("找不到该账号。");
        }
        RechargeOrder priorRequest = lambdaQuery().eq(RechargeOrder::getOwnerId, ownerId)
                .eq(RechargeOrder::getRequestKey, requestKey).one();
        if (priorRequest != null) return RechargeViews.Order.from(priorRequest);
        if (request.amount().stripTrailingZeros().scale() > 0) {
            throw WorkspaceException.badRequest("充值金额必须为整数元。");
        }
        PaymentImages.Receipt receipt = images.readReceipt(request.receipt());
        RechargeOrder priorReceipt = lambdaQuery().eq(RechargeOrder::getOwnerId, ownerId)
                .eq(RechargeOrder::getReceiptSha256, receipt.sha256())
                .in(RechargeOrder::getState, RechargeOrder.State.PENDING, RechargeOrder.State.CREDITED).one();
        if (priorReceipt != null) {
            String state = priorReceipt.getState() == RechargeOrder.State.CREDITED ? "已入账" : "待处理";
            throw WorkspaceException.conflict("这张付款截图已用于「" + state + "」的申请 " + priorReceipt.getRequestNumber()
                    + "。可在「申请记录」查看。若是另一笔付款，请上传对应的付款截图。");
        }
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
        order.setState(RechargeOrder.State.PENDING);
        order.setReviewNote("");
        save(order);
        return RechargeViews.Order.from(order);
    }

    @Override
    public ImageView receipt(long ownerId, long id) {
        RechargeOrder order = lambdaQuery().eq(RechargeOrder::getOwnerId, ownerId).eq(RechargeOrder::getId, id).one();
        return receiptImage(requireOrder(order));
    }

    @Override
    public AdminViews.Page<AdminViews.Order> reviewQueue(RechargeOrder.State state, int page) {
        List<RechargeOrder> rows = lambdaQuery().eq(state != null, RechargeOrder::getState, state)
                .orderByDesc(RechargeOrder::getId)
                .last("LIMIT " + (PAGE_SIZE + 1) + " OFFSET " + (long) (page - 1) * PAGE_SIZE).list();
        List<RechargeOrder> items = rows.stream().limit(PAGE_SIZE).toList();
        if (items.isEmpty()) return new AdminViews.Page<>(List.of(), false);
        Map<Long, String> phones = accounts.selectByIds(items.stream().map(RechargeOrder::getOwnerId).distinct().toList())
                .stream().collect(Collectors.toMap(Account::getId, Account::getPhone));
        return new AdminViews.Page<>(items.stream().map(order -> new AdminViews.Order(order.getOwnerId(),
                phones.get(order.getOwnerId()), RechargeViews.Order.from(order))).toList(), rows.size() > PAGE_SIZE);
    }

    @Override
    public ImageView reviewReceipt(long id) {
        return receiptImage(requireOrder(getById(id)));
    }

    @Override
    @Transactional
    public RechargeViews.Order credit(long operatorId, long id, int points, String note) {
        accounts.lock(requireOrder(getById(id)).getOwnerId());
        RechargeOrder order = requireOrder(baseMapper.lock(id));
        String reviewNote = Objects.toString(note, "").strip();
        if (order.getState() == RechargeOrder.State.CREDITED && Objects.equals(order.getCreditedPoints(), points)
                && order.getReviewNote().equals(reviewNote)) {
            return RechargeViews.Order.from(order);
        }
        requirePending(order);
        accountService.addPoints(order.getOwnerId(), operatorId, null, id, points,
                reviewNote.isEmpty() ? "充值 " + order.getRequestNumber() : reviewNote);
        order.setCreditedPoints(points);
        return review(order, operatorId, RechargeOrder.State.CREDITED, reviewNote);
    }

    @Override
    @Transactional
    public RechargeViews.Order reject(long operatorId, long id, String note) {
        RechargeOrder order = requireOrder(baseMapper.lock(id));
        String reviewNote = note.strip();
        if (order.getState() == RechargeOrder.State.REJECTED && order.getReviewNote().equals(reviewNote)) {
            return RechargeViews.Order.from(order);
        }
        requirePending(order);
        return review(order, operatorId, RechargeOrder.State.REJECTED, reviewNote);
    }

    private RechargeViews.Order review(RechargeOrder order, long operatorId, RechargeOrder.State state, String note) {
        order.setState(state);
        order.setReviewedBy(operatorId);
        order.setReviewedAt(OffsetDateTime.now());
        order.setReviewNote(note);
        updateById(order);
        return RechargeViews.Order.from(order);
    }

    private static void requirePending(RechargeOrder order) {
        if (order.getState() != RechargeOrder.State.PENDING) {
            throw WorkspaceException.conflict("这笔充值已经处理。");
        }
    }

    private static RechargeOrder requireOrder(RechargeOrder order) {
        if (order == null) {
            throw WorkspaceException.notFound("找不到该充值申请。");
        }
        return order;
    }

    private ImageView receiptImage(RechargeOrder order) {
        return new ImageView(files.download(order.getReceiptKey()), MediaType.valueOf(order.getReceiptType()));
    }
}
