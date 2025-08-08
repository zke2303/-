package com.hmdp.service.impl;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.sun.org.apache.xpath.internal.operations.Bool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.naming.ldap.Rdn;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    private StringRedisTemplate stringRedisTemplate;

    private CacheClient cacheClient;

    @Autowired
    public ShopServiceImpl(StringRedisTemplate stringRedisTemplate
    , CacheClient cacheClient){
        this.stringRedisTemplate = stringRedisTemplate;
        this.cacheClient = cacheClient;
    }




    /**
     * 根据id查询商铺信息, 用过redis来实现
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result queryById(Long id) throws InterruptedException {
        Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY,
                id,
                Shop.class,
                this::getById,
                RedisConstants.CACHE_SHOP_TTL,
                TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("店铺不存在!");
        }

        return Result.ok(shop);
    }

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long shopId = shop.getId();
        if (shopId == null) {
            return Result.fail("店铺的id不能为空");
        }

        // 1.先操作数据库
        updateById(shop);
        // 2.采用删除策略，删除缓存中的数据
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.delete(key);
        return Result.ok(shop);
    }


    /**
     * 将shop对象保存到redis中，并添加logical expired
     * @param id 店铺id
     * @param time 逻辑过期时间
     * @param timeUnit 时间单位
     */
    @Override
    public void saveShopWithLogicalExpired(Long id, Long time, TimeUnit timeUnit) {
        // 1.查询数据库
        Shop shop = getById(id);
        // 2.封装成RedisData对象
        RedisData redisData = RedisData.builder()
                .data(shop)
                .expireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)))
                .build();
        String key = RedisConstants.CACHE_SHOP_KEY + shop.getId();
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }
}
