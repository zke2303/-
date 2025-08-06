package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

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

        // 4.将验证码保存到session中
        session.setAttribute("code", code);

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
        // 1.校验手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误!");
        }

        // 2.校验验证码
        String code = loginForm.getCode();
        String cacheCode = (String) session.getAttribute("code");
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误！");
        }

        // 3.查询用户, 通过MybatisPlus实现简单的sql语句
        User user = query().eq("phone", loginForm.getPhone()).one();
        // 4.判断当前用户是否存在
        if (user == null) {
            // 不存在，则创建一个新用户保存到数据库中
            user = createUserWithPhone(loginForm.getPhone());
        }
        // 5.保存用户信息到session中
        session.setAttribute("user", user);

        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        return User.builder()
                .phone(phone)
                .build();
    }
}
