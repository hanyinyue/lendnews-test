package com.heima.wemedia.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
//强行扫描到这个包
@ComponentScan("com.heima.apis.acticle.fallback")
public class InitConfig {
}