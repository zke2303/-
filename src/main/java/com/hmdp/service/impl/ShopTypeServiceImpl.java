package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询商铺类型列表
     * @return 返回所有类型的商铺
     */

    @Override
    public Result queryTypeList() {
        // 1.从缓存中查询商铺列表
        String key = RedisConstants.CACHE_SHOP_KEY + "list";
        String shopTypeList = stringRedisTemplate.opsForValue().get(key);
        // 2.判断是否存在
        if (!StrUtil.isEmpty(shopTypeList)) {
            // 存在， 返回结果, 不需要排序，因为在查询数据时已经排序好了
            List<ShopType> list = JSONUtil.toList(shopTypeList, ShopType.class);
            return Result.ok(list);
        }
        // 3.不存在，查询数据库
        List<ShopType> typeList = query().orderByAsc("sort").list();
        // 4.判断数据库中是否存在
        if (typeList.isEmpty()) {
            // 不存在，返回错误信息
            return Result.fail("不存在商铺");
        }

        // 5.存在， 保存到缓存中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList));

        return Result.ok(typeList);
    }
}
