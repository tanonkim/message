package com.message.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("알림 게이트웨이 API")
                        .version("v1")
                        .description("SMS, 카카오 알림톡, 이메일, 푸시 알림 통합 발송 게이트웨이"));
    }
}