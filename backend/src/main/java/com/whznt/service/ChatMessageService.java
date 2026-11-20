package com.whznt.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.whznt.entity.ChatMessage;

import java.util.List;

public interface ChatMessageService extends IService<ChatMessage> {

    List<ChatMessage> forConversation(long conversationId);

    List<ChatMessage> recent(long conversationId);

    void appendExchange(long conversationId, String input, String answer);
}
