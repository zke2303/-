package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送手机验证码
     * @param phone 手机号
     * @param session session端
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            // 2.手机机号不符合要求，返回错误信息
            return Result.fail("手机号格式错误！");
        }

        // 3.生成一个六位数的验证码
        String code = RandomUtil.randomNumbers(6);

//        // 4.将验证码保存到session中
//        session.setAttribute("code", code);

        // 4.将验证码保存到redis中, 有效期为一分钟
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone,
                code,
                RedisConstants.LOGIN_CODE_TTL,
                TimeUnit.MINUTES);

        // 5.调用API，通过短信发送验证码
        log.info("验证码: {}", code);

        // 6.返回验证码
        return Result.ok();
    }

    /**
     * 登录功能
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();

        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误!");
        }

        // 2.校验验证码
        String code = loginForm.getCode();
//        String cacheCode = (String) session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);

        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误！");
        }

        // 3.查询用户, 通过MybatisPlus实现简单的sql语句

        User user = query().eq("phone", phone).one();
        // 4.判断当前用户是否存在
        if (user == null) {
            // 不存在，则创建一个新用户保存到数据库中
            user = createUserWithPhone(phone);
        }

//        // 5.保存用户信息到session中
//        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        // 5.保存用户信息到redis中, 使用uuid作为token
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        Map<String, Object> map = BeanUtil.beanToMap(userDTO);

        String token = UUID.randomUUID().toString();

        String key = RedisConstants.LOGIN_USER_KEY + token;

        stringRedisTemplate.opsForHash().putAll(key, map);

        // 设置token的有效期, 10分钟
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.SECONDS);

        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        return User.builder()
                .phone(phone)
                .nickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10))
                .build();
    }
}
