package com.whznt.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.whznt.entity.ChatMessage;
import com.whznt.mapper.ChatMessageMapper;
import com.whznt.service.ChatMessageService;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage> implements ChatMessageService {

    @Override
    public List<ChatMessage> forConversation(long conversationId) {
        return lambdaQuery().eq(ChatMessage::getConversationId, conversationId).orderByAsc(ChatMessage::getId).list();
    }

    @Override
    public List<ChatMessage> recent(long conversationId) {
        List<ChatMessage> messages = lambdaQuery().eq(ChatMessage::getConversationId, conversationId)
                .orderByDesc(ChatMessage::getId).last("LIMIT 24").list();

        int textBudget = 24000;
        int count = 0;
        while (count + 1 < messages.size()) {
            int length = messages.get(count).getContent().length() + messages.get(count + 1).getContent().length();
            if (length > textBudget && count != 0) {
                break;
            }
            textBudget -= length;
            count += 2;
        }
        List<ChatMessage> history = messages.subList(0, count);
        Collections.reverse(history);
        return history;
    }

    @Override
    public void appendExchange(long conversationId, String input, String answer) {
        append(conversationId, ChatMessage.Role.USER, input);
        append(conversationId, ChatMessage.Role.ASSISTANT, answer);
    }

    private void append(long conversationId, ChatMessage.Role role, String content) {
        ChatMessage message = new ChatMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setCreatedAt(OffsetDateTime.now());
        save(message);
    }
}
