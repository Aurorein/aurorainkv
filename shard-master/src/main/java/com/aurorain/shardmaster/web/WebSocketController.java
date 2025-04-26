package com.aurorain.shardmaster.web;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;

@Controller
@CrossOrigin("*")
public class WebSocketController {

    @MessageMapping("/sendLog")
    @SendTo("/topic/logs")
    public String sendLog(String logMessage) {
        return logMessage;
    }
}