package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * 缓存Redis的工具类
 */

@Component
@Slf4j
public class CacheClient{

    private final StringRedisTemplate stringRedisTemplate;

    @Autowired
    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将任意对象序列化成Json对象并存储到String类型的key中，并且可以设置TTL过期时间
     * @param key redis中的key
     * @param value redis中的value，也就是转换成Json的对象
     * @param time 过期时间
     * @param timeUnit 时间单位
     */
    public void saveObject2Redis(String key, Object value, Long time, TimeUnit timeUnit) {
        // 存储到String类型的key中, 并设置TTL
        stringRedisTemplate.opsForValue().set(key,
                JSONUtil.toJsonStr(value),
                time,
                timeUnit);
    }


    /**
     * 将任意对象序列化成Json对象并存储到String类型的key中，并且可以设置逻辑过期时间
     * @param key redis中的key
     * @param value redis中的value，也就是转换成Json的对象
     * @param time 过期时间
     * @param timeUnit 时间单位
     */
    public void saveObject2RedisWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit){
        // 封装成RedisData对象
        RedisData redisData = RedisData.builder()
                .data(value)
                .expireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)))
                .build();

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透的问题
     * @param keyPrefix key的前缀名称
     * @param id 查询对象的id
     * @param type 放回兑现的类型
     * @param dbFallback sql查询语句
     * @return 任意类型对象
     * @param <R> 返回类型的泛型
     * @param <ID> id的类型的泛型
     */
    public <R, ID> R queryWithPassThrough(String keyPrefix,
                                          ID id,
                                          Class<R> type,
                                          Function<ID, R> dbFallback,
                                          Long time,
                                          TimeUnit timeUnit) {
        String key = keyPrefix + id;
        // 1.在redis中更加key查询数据
        String objectJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (StrUtil.isNotBlank(objectJson)) {
            // 3.存在，直接返回查询结果
            return JSONUtil.toBean(objectJson, type);
        }

        // 4.不存在, 判断是否命中空值
        if (StrUtil.equals("", objectJson)) {
            // 命中空值，返回null
            return null;
        }

        // 5.不存在， 根据id查询数据库
        R r = dbFallback.apply(id);

        // 6.判断r是否存在
        // 6.1 不存在， 缓存空值,并返回null
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key,
                    "",
                    time,
                    timeUnit);
            return null;
        }

        // 6.2 存在， 缓存查询到的对象
        stringRedisTemplate.opsForValue().set(key,
                JSONUtil.toJsonStr(r),
                time,
                timeUnit);

        return r;
    }

    private static final ExecutorService executorService = Executors.newFixedThreadPool(10);


    /**
     * 通过逻辑过期解决缓存击穿
     * @param keyPrefix
     * @param id
     * @param type
     * @param dbFallback
     * @param time
     * @param timeUnit
     * @return
     * @param <R>
     * @param <ID>
     */
    public <R, ID> R queryWithLogicalExpire(String keyPrefix,
                                            ID id,
                                            Class<R> type,
                                            Function<ID, R> dbFallback,
                                            Long time,
                                            TimeUnit timeUnit){
        String key = keyPrefix + id;
        // 1.查询redis数据库
        String objectJson = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否命中
        if (StrUtil.isBlank(objectJson)) {
            // 2.1 未命中，直接返回null
            return null;
        }

        // 命中， 判断是否逻辑过期
        RedisData redisData = JSONUtil.toBean(objectJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();

        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期， 返回查询结果
            return r;
        }

        // 已过期， 查询数据库，进行缓存重建
        String lockKey = RedisConstants.LOCK_KEY + type.getSimpleName() + ":" + id;
        // 尝试获取锁
        String lockValue = tryLock(lockKey);

        if (StrUtil.isNotBlank(lockValue)) {
            // 获取锁成功， 创建一个线程去重建缓存
            log.info("成功获取锁，启动后台线程重建缓存...");
            executorService.submit(() -> {
               try {
                    // 查询数据库
                   R r1 = dbFallback.apply(id);
                   // 写入redis中
                   setWithLogicalExpire(key, r1, time, timeUnit);
               }catch (Exception e){
                   log.error("后台线程重建缓存失败", e);
               }finally {
                   // 释放锁
                   unLock(lockKey, lockValue);
               }
            });

        }

        return r;
    }


    /**
     * 加锁，使用 UUID 确保锁的安全性
     * @param lockKey 锁的键
     * @return 是否成功获取锁
     */
    private String tryLock(String lockKey){
        String uuid = UUID.randomUUID().toString(true);
        Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(lockKey,
                uuid,
                RedisConstants.LOCK_SHOP_TTL,
                TimeUnit.MINUTES);

        if(Boolean.TRUE.equals(result)){
            // 上锁成功
            return uuid;
        }
        return null;
    }


    /**
     * 解锁，解锁前校验锁的值
     * @param lockKey 锁的键
     * @param value 锁的值，用于校验
     */
    private void unLock(String lockKey, String value){
        try {
            String storeValue = stringRedisTemplate.opsForValue().get(lockKey);
            // 校验锁的值是否一致，防止误删
            if (StrUtil.equals(storeValue, value)) {
                stringRedisTemplate.delete(lockKey);
            }
        }catch (Exception e){
            log.error("释放锁失败", e);
        }
    }


    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit timeUnit) {
        // 封装成RedisData对象
        RedisData redisData = RedisData.builder()
                .data(value)
                .expireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)))
                .build();

        // 将封装的结果保存到redis中, 不设置TTL
        stringRedisTemplate.opsForValue().set(key,
                JSONUtil.toJsonStr(redisData));
    }

}
