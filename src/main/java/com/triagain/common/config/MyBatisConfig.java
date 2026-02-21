package com.triagain.common.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan(basePackages = {
        "com.triagain.user.infra",
        "com.triagain.crew.infra",
        "com.triagain.verification.infra",
        "com.triagain.moderation.infra",
        "com.triagain.support.infra"
})
public class MyBatisConfig {
}
