package com.hmdp.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author:张鹏
 * @description:
 * @date: 2023/1/1 16:09
 */

@Data
public class RedisData {
    private LocalDateTime expiretime;
    private Object data;
}
