package com.hmdp.config;

import com.hmdp.intercepter.LoginIntercepter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //添加拦截器
        registry.addInterceptor(new LoginIntercepter())
                .excludePathPatterns(
                        "/user/login",
                        "/user/code",
                        "/blog/hot",
                        "shop/**",
                        "shop-type/**",
                        "upload/**",
                        "voucher/**"

                );
        //配置拦截路径
        WebMvcConfigurer.super.addInterceptors(registry);
    }
}
