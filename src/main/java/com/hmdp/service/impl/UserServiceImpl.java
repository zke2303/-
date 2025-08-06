package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
}
