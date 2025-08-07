package com.hmdp.interceptor;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 这个拦截器用来是用来刷新token的，在后面还有一个拦截器用来token校验，这个拦截器只是用来刷新
 * token的，因此不拦截任何请求
 */
@Component
public class RefreshTokenInterception implements HandlerInterceptor {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.从请求头中获取token
        String token = request.getHeader("authorization");
        // 2.判断token是否存在
        if (StrUtil.isBlank(token)){
            // 不存在直接放行
            return true;
        }
        // 3.存在token，查询redis中的用户
        String key = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> map = stringRedisTemplate.opsForHash().entries(key);
        // 4.判断用户是否存在
        if (map.isEmpty()){
            return true;
        }
        UserDTO userDTO = BeanUtil.copyProperties(map,UserDTO.class);
        // 6.用户存在，将用户信息保存到ThreadLocal中
        UserHolder.saveUser(userDTO);

        // 7.用户存在，更新redis中用户的有效期
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        // 8.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
