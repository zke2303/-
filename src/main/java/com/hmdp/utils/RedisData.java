package com.hmdp.utils;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;
}
