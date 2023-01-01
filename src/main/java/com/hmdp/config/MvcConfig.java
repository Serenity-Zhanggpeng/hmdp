package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

/**
 * @author:张鹏
 * @description:  Mvc配置类将拦截器添加进来
 * @date: 2022/12/22 18:31
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
//        registry.addInterceptor(new RefreshTokenIntercepter(stringRedisTemplate))
//                .order(0);  //先执行

        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate)) //拦截器默认全部路径都拦截
                .excludePathPatterns(
                        "/user/login",
                        "/user/code",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/voucher/**",
                        "/upload/**"
                );

    }


}
