package com.hmdp.utils;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class RedisIdWorker {
    // 秒级时间戳
    private static final int COUNT_BITS = 32;
    private static final long BEGIN_TIMESTAMP = 1704067200L;
    private final StringRedisTemplate stringRedisTemplate;

    public long nextId(String keyPrefix){
        // 获取时间戳：当前时间减去开始时间
        long currentSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.of("+8"));
        long timeStamp = currentSecond - BEGIN_TIMESTAMP;
        // 获得序列号:使用redis自增结合当前日期生成
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        long count = stringRedisTemplate.opsForValue().increment("icr:"+keyPrefix+":"+date);
        // 进行拼接
        return timeStamp<<COUNT_BITS|count;
    }
}
