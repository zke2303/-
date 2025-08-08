package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    Result queryById(Long id) throws InterruptedException;

    /**
     * 更新商铺信息
     * @param shop 商铺数据
     */
    Result update(Shop shop);


    /**
     * 将shop对象保存到redis中，并添加logical expired
     * @param id 店铺id
     * @param time 逻辑过期时间
     * @param timeUnit 时间单位
     */
    void saveShopWithLogicalExpired(Long id, Long time, TimeUnit timeUnit);
}

