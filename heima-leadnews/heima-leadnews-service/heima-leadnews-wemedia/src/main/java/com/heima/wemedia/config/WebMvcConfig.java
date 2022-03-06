package com.heima.wemedia.config;

import com.heima.wemedia.interceptor.WmTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
//重写web中自带的拦截器
public class WebMvcConfig implements WebMvcConfigurer {
    //重写拦截器的方法
    //添加自定义的拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //addPathPatterns()方法 添加拦截路径 拦截所有
        registry.addInterceptor(new WmTokenInterceptor())
                .addPathPatterns("/**").excludePathPatterns("/login/**");
    }
}
