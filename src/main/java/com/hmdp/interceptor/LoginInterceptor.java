package com.hmdp.interceptor;

import ch.qos.logback.core.util.TimeUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class LoginInterceptor implements HandlerInterceptor {

    // Controller执行完毕后，重新回到拦截器中执行的操作
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 清除用户登入信息
        UserHolder.removeUser();
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    // 执行Controller之前，执行下列代码
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 基于session校验
//        // 1.获取session
//        HttpSession session = request.getSession();
//        // 2.获取session中的信息
//        UserDTO user = (UserDTO) session.getAttribute("user");
//        // 3.判断user是否为空
//        if (user == null) {
//            // 为空，拦截这个请求
//            return false;
//        }
//        // 4.用户存在，保存信息到ThreadLocal中
//        UserHolder.saveUser(user);
//        // 5.不为空，放行
//        return true;

        // 基于redis校验

        // 1.获取请求头中的token
//        String token = request.getHeader("authorization");
//        // 判断请求是否携带token
//        if (StrUtil.isBlank(token)) {
//            // 请求没有携带token，并返回401状态码
//            response.setStatus(401);
//            return false;
//        }
//        // 2.根据token从redis中获取用户信息
//        String key = RedisConstants.LOGIN_USER_KEY + token;
//        Map<Object, Object> user = stringRedisTemplate.opsForHash().entries(key);
//        // 3.判断用户是否存在
//        if (user.isEmpty()) {
//            // 用户不存在拦截该请求，并返回401状态码
//            response.setStatus(401);
//            return false;
//        }
//
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(user, new UserDTO(), false);
//
//        // 用户存在，将用户信息保存到ThreadLocal中
//        UserHolder.saveUser(userDTO);
//
//        // 更新redis中用户的有效期
//        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);
//        return true;

        // 判断ThreadLocal中是否有用户
        if (UserHolder.getUser() == null) {
            // 无用户，进行拦截，设置响应状态码
            response.setStatus(401);
            return false;
        }

        // 有用户放行
        return true;
    }
}
