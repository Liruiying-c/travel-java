package org.example.traveljava.vo;


import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TravelSearchVO {
    private Integer userId;
    private Integer id;
    private Integer tripId;
    private String city;
    private Integer days;
    private Double totalBudget;
    private LocalDateTime createTime;
    private boolean isCollected = false;


}
