package org.example.traveljava.vo;


import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TravelSearchVO {
    private Integer tripId;
    private String city;
    private Integer days;
    private Double budget;
    private LocalDateTime createTime;
    private boolean isCollected = false;


}
