package com.hmdp.config;

import com.hmdp.intercepter.FreshTokenIntercepter;
import com.hmdp.intercepter.LoginIntercepter;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //添加拦截器
        registry.addInterceptor(new LoginIntercepter())
                .excludePathPatterns(
                        "/user/login",
                        "/user/code",
                        "/blog/hot",
                        "/shop/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/voucher/**"

                ).order(1);
        //order的值越大，其拦截器越后执行
        registry.addInterceptor(new FreshTokenIntercepter(stringRedisTemplate))
                .addPathPatterns("/**").order(0);
        //配置拦截路径
        WebMvcConfigurer.super.addInterceptors(registry);
    }
}
