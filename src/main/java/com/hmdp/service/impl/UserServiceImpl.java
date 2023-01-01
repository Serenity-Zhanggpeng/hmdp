package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1验证手机号是否符合
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2如果不符合，返回错误信息
            return Result.fail("手机号格式输入错误");
        }

        //3手机号符合生成验证码
        String validatedCode = RandomUtil.randomNumbers(6);

        //保存验证码到session      session.setAttribute("code", validatedCode);
        //保存验证码到redis                       set key value ex
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, validatedCode, 5, TimeUnit.MINUTES);

        //5发送验证码   //这里可以短信 也可qq邮箱
        log.debug("发送验证码成功，验证码:" + validatedCode);
        return Result.ok();


    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //验证手机号是否符合
        if (RegexUtils.isPhoneInvalid(loginForm.getPhone())) {
            //无效的手机号
            return Result.fail("手机号格式有误!");
        }

        //验证验证码
        //从session获取验证码
        //String validatedCode = (String) session.getAttribute("code");

        //从redis中获取验证码
        String validatedCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + loginForm.getPhone());

        String code = loginForm.getCode(); //前端输入的验证码

        //不一致报错
        if (validatedCode == null || !validatedCode.equals(code)) {
            return Result.fail("验证码错误");
        }

        //一致，根据手机号查询用户
        User user = this.query().eq("phone", loginForm.getPhone()).one();
        if (user == null) {
            //不存在，创建用户并保存
            user = createUserByPhone(loginForm.getPhone());
        }

        //保存用户信息到session
        //session.setAttribute("user", user); 敏感信息太多数据泄露
        //session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));

        //数据保存到redis中   随机生成token，作为登录令牌 将user转为hash储存  储存   返回token
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userToMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));  //思考这里的转为map key和value为？？？？
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        //存储
        stringRedisTemplate.opsForHash().putAll(tokenKey, userToMap);

        //设置tokenKey的有效期  这里设置key的有效期是30分后立马过期 而原来的seesion的有效期是当用户操作30分钟有效期就被重设
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.SECONDS);
        return Result.ok(token);
    }

    private User createUserByPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        this.save(user);
        return user;
    }

}
