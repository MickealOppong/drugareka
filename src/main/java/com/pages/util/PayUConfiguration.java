package com.pages.util;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PayUProperties.class)
public class PayUConfiguration {
}
