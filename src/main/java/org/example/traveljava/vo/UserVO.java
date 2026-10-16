package org.example.traveljava.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserVO {
    private Integer id;
    private String username;
    private String passwordHash;
    private String nickname;
    private String avatar;
    private LocalDateTime createTime;
}
