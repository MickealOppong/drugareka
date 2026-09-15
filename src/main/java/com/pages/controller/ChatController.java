package com.pages.controller;

// ChatController.java
import com.pages.dto.ChatMessageDto;
import com.pages.model.ChatMessage;
import com.pages.service.AppUserDetailsService;
import com.pages.service.ChatMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@Slf4j
@Controller
@RestController
public class ChatController {

    @Autowired
    private ChatMessageService chatMessageService;
    @Autowired
    private  AppUserDetailsService appUserDetailsService;


    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    // 1. REST Endpoint to load historical messages when clicking the match card
    @GetMapping("/api/messages/{matchId}")
    public List<ChatMessageDto> getChatHistory(@PathVariable Long matchId) {

        return chatMessageService.findByMatch(matchId);
    }

    // 2. WebSocket Mapping for processing live messages
    // Client sends to: /app/chat/{matchId}
    @MessageMapping("/chat/{matchId}")
    public void processMessage( @DestinationVariable Long matchId, @Payload ChatMessageDto message) {
        // Save to Database
        ChatMessage savedMessage = chatMessageService.saveMessage(message);
        ChatMessageDto messageDto = ChatMessageDto.builder()
                .id(savedMessage.getId())
                .message(savedMessage.getMessage())
                .senderId(savedMessage.getSender().getId())
                .receiverId(savedMessage.getReceiver().getId())
                .build();

        // Broadcast live to subscribers listening at: /topic/messages/{matchId}
        messagingTemplate.convertAndSend( "/topic/messages/"+matchId,messageDto);

    }
}
