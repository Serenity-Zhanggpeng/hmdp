package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * @author:  张鹏
 * @description:   拦截器                拦截器还要再在MVC配置类中添加
 * @date: 2022/12/22 18:15
 */
public class LoginInterceptor implements HandlerInterceptor {
    //这里LoginInterceptor是new出来的没有交给spring 所以不能注入
    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //HttpSession httpSession = request.getSession();

        //从请求头获取token        if(token) config.headers['authorization'] = token 前端拦截器
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            //没有token值说明没有登录     登陆的时候后端把token值给了前端
            response.setStatus(401);   //返回401状态码       前端拦截器 if(error.response.status == 401)
            return false;
        }

        //httpsession  获取在登录的时候存放的对象  UserDTO userDTO = (UserDTO) httpSession.getAttribute("user");

        String key=RedisConstants.LOGIN_USER_KEY + token;
        //基于token获取redis中的用户
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(key);

        //判断用户是否为空
        if (userMap.isEmpty()) {
            //user过期或根本没登录
            response.setStatus(401);   //返回401状态码
            return false;
        }


        //将查到的hasp数据转为UserDTO对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // session可也得到user对象 说明登录了  将用户信息保存到ThreadLocal 便于给xxController层传递user
        UserHolder.saveUser(userDTO);

        //刷新token有效期
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //放行
        return true;
    }

    //afterCompletion线程处理完之后执行  所以移除用户
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //将ThreadLocal中的user信息移除
        UserHolder.removeUser();
    }
}
