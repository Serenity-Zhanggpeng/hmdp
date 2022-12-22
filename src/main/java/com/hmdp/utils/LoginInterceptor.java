package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import static net.sf.jsqlparser.util.validation.metadata.NamedObject.user;

/**
 * @author:  张鹏
 * @description:   拦截器                拦截器还要再在MVC配置类中添加
 * @date: 2022/12/22 18:15
 */
public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        HttpSession httpSession = request.getSession();
        //httpsession  获取在登录的时候存放的对象
        UserDTO userDTO = (UserDTO) httpSession.getAttribute("user");
        if (userDTO == null) {
            //user过期或根本没登录
            response.setStatus(401);   //返回401状态码
            return false;
        }
        // session可也得到user对象 说明登录了  将用户信息保存到ThreadLocal 便于给xxController层传递user
        UserHolder.saveUser(userDTO);
       return true;
    }

    //afterCompletion线程处理完之后执行  所以移除用户
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //将ThreadLocal中的user信息移除
        UserHolder.removeUser();
    }
}
