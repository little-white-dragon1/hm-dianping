package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
//这个标志操作在idea中的控制台中打印的日志信息
@Slf4j
@Service
@SuppressWarnings({"all"})
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误，请输入正确的手机号!");
        }
        //利用hutool的随机生成函数生成一个六位数的验证码
        String code = RandomUtil.randomNumbers(6);
        //session.setAttribute("code",code);
        //用redis来进行存储，解决不同tomcat服务器之间的数据不互通的问题
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("发送短信验证码成功！{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //验证手机号
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            return Result.fail("手机号格式错误，请输入正确的手机号!");
        }
        //验证短信验证码
        Object scode = session.getAttribute("code");
        String code = loginForm.getCode();
        if (scode == null || !scode.toString().equals(code)) {
            return Result.fail("验证码错误或为空！");
        }
        //根据手机号查询用户是否已经注册过
        User user = query().eq("phone", loginForm.getPhone()).one();
        if (user == null) {
            user = createUserByPhone(loginForm.getPhone());
        }
        //若存在，将用户信息保存在sesion中
        //用hetool中的工具将user属性复制到UserDTO中，可以避免我们自己new一个新对象在一个一个的set
        session.setAttribute("User", BeanUtil.copyProperties(user, UserDTO.class));
        return Result.ok();
    }

    public User createUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(4));
        save(user);
        return user;
    }
}
