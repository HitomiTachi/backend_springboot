package com.example.webdienthoai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ VnpayProperties.class, MomoProperties.class, AppUrlProperties.class })
public class PaymentConfig {
}
