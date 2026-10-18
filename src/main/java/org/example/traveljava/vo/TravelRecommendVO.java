package org.example.traveljava.vo;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;


import java.util.List;

@Data
@TableName(value = "t_trip", autoResultMap = true)
public class TravelRecommendVO {
    private Integer userId;
    private Integer tripId;
    private Integer id;
    private Boolean success;
    private String city;
    private Integer days;
    private Double totalBudget;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<DailyItinerary> dailyItinerary;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private BudgetBreakdown  budgetBreakdown;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tips;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> warnings;

    private String error;
    private String rawResponse;

    @Data
    public static class DailyItinerary {
        private Integer day;
        private String date;
        private Timeslot morning;
        private Timeslot afternoon;
        private Timeslot evening;
    }

    @Data
    public static class Timeslot {
        private String spot;
        private String duration;
        private String transportation;
        private String description;
        private String ticket;
    }

    @Data
    public static class BudgetBreakdown {
        private Double accommodation;
        private Double food;
        private Double transportation;
        private Double tickets;
        private Double other;
    }

}
