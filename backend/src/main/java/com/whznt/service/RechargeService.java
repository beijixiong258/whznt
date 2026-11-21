package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.dto.RechargeRequests;
import com.whznt.entity.RechargeOrder;
import com.whznt.integration.PaymentImages.ImageView;
import com.whznt.vo.RechargeViews;

import java.util.List;

public interface RechargeService extends IService<RechargeOrder> {

    RechargeViews.Settings settings();

    ImageView qrImage();

    List<RechargeViews.Order> orders(long ownerId);

    RechargeViews.Order submit(long ownerId, RechargeRequests.Submit request);

    ImageView receipt(long ownerId, long id);
}
