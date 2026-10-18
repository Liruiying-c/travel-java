package org.example.traveljava.dto;

import jakarta.validation.constraints.*;
import lombok.Data;


// 参数校验 Spring Validation

@Data
public class TravelRequestDto {
    private Integer userId;

    @NotNull(message = "城市不能为空")
    private String city;


    @NotNull(message = "天数不能为空")
    @Min(value = 1, message = "天数不能小于1")
    @Max(value = 30, message = "天数不能大于30")
    private Integer days;


    @NotNull(message = "预算不能为空")
    @Min(value = 100, message = "预算不能小于100")
    @Max(value = 1000000, message = "预算不能大于1000000")
    private Double budget;
}
