package org.example.traveljava.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatRequestDto {
    @NotNull(message = "消息不能为空")
    private String message;
}
