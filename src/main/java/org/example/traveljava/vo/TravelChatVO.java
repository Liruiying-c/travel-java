package org.example.traveljava.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TravelChatVO {
    private Integer chatId;
    private String userMsg;
    private LocalDateTime userTime;
    private String aiMsg;
    private LocalDateTime aiTime;
}
