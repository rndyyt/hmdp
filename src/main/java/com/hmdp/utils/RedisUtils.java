package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisUtils {
    public static final String ID_PREFIX = UUID.randomUUID().toString() + "-";
    public static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = new ThreadPoolExecutor(
            10,
            10,
            0,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(10),
            Executors.defaultThreadFactory(),
            new ThreadPoolExecutor.AbortPolicy());

    public <R> R queryWithMutex(String prefix,Long id, Class<R> type, Supplier<R> dbFallback){
        // 查询缓存
        String key = prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 缓存命中则返回
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json,type);
        }
        // 如果是防缓存击穿的空值则返回
        if(json != null){
            return null;
        }
        // 获取互斥锁
        String lockKey = CACHE_MUTEX_KEY + id;
        R r = null;
        // 如果获取不到锁则休眠后重试
        if(!tryLock(lockKey)){
            try {
                TimeUnit.MILLISECONDS.sleep(50);
                return queryWithMutex(prefix,id,type,dbFallback);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        // 互斥锁逻辑用try-finally包裹，确保解锁
        try {
            // 获取到锁后先查看是否有其他线程完成了查询
            String doubleCheck = stringRedisTemplate.opsForValue().get(key);
            // 缓存命中则返回
            if(StrUtil.isNotBlank(doubleCheck)){
                return JSONUtil.toBean(doubleCheck,type);
            }
            // 如果是防缓存击穿的空值则返回
            if(doubleCheck != null){
                return null;
            }
            // 查询数据库
            r = dbFallback.get();
            // 写入缓存
            if(r == null){
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            }else {
                stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(r),CACHE_SHOP_TTL,TimeUnit.MINUTES);
            }
        }catch (Exception e){
            throw new RuntimeException(e);
        }finally {
            unlock(lockKey);
        }
        log.info(">>>互斥锁运行正常");
        return r;
    }

    public <R> R queryWithLogicalExpire(String prefix,Long id,Class<R> type,Supplier<R> dbFallback){
        // 查询缓存
        String key = prefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 缓存未命中则直接返回（逻辑过期方法用于解决缓存击穿，只关注热点数据）
        if(StrUtil.isBlank(json)){
            return null;
        }
        // 命中后查看过期时间
        JSONObject redisDataJson = JSONUtil.parseObj(json);
        R r = JSONUtil.toBean(redisDataJson.getJSONObject("data"),type);
        LocalDateTime expireTime = redisDataJson.getLocalDateTime("expireTime",null);
        // 未过期直接返回
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        // 过期则尝试获取互斥锁
        String lockKey = CACHE_MUTEX_KEY + id;
        // 获取成功则开启新线程进行缓存重建
        if(tryLock(lockKey)){
            CACHE_REBUILD_EXECUTOR.submit(()->{
                try {
                    //查询数据库
                    R data = dbFallback.get();
                    //添加过期时间后保存到redis
                    this.setWithLogicalExpire(key,data);
                }catch (Exception e){
                    throw new RuntimeException(e);
                }finally {
                    unlock(lockKey);
                }
            });
        }
        // 返回旧数据
        return r;
    }

    private <T> void set(String key,T data){
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(data));
    }

    private <T> void setWithLogicalExpire(String key,T data) {
        RedisData redisData = new RedisData();
        redisData.setData(data);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(CACHE_LOGICAL_TTL));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    public void unlock(String lockKey) {
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(lockKey),
                threadId);
    }

    public boolean tryLock(String lockKey) {
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey,threadId,30, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(lock);
    }
}
