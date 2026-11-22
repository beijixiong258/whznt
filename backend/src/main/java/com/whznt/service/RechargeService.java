package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.dto.RechargeRequests;
import com.whznt.entity.RechargeOrder;
import com.whznt.integration.PaymentImages.ImageView;
import com.whznt.vo.AdminViews;
import com.whznt.vo.RechargeViews;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RechargeService extends IService<RechargeOrder> {

    RechargeViews.Settings settings(long ownerId);

    ImageView qrImage();

    void saveQr(MultipartFile file);

    List<RechargeViews.Order> orders(long ownerId);

    RechargeViews.Order submit(long ownerId, RechargeRequests.Submit request);

    ImageView receipt(long ownerId, long id);

    AdminViews.Page<AdminViews.Order> reviewQueue(RechargeOrder.State state, int page);

    ImageView reviewReceipt(long id);

    RechargeViews.Order credit(long operatorId, long id, int points, String note);

    RechargeViews.Order reject(long operatorId, long id, String note);
}
