package com.jameati.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
  @Bean OpenAPI api() { return new OpenAPI().info(new Info().title("Jameati API").version("v1").description("منصة جامعتي للفرص التعليمية"))
      .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
      .addSecurityItem(new SecurityRequirement().addList("accessCookie"))
      .components(new io.swagger.v3.oas.models.Components()
          .addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT"))
          .addSecuritySchemes("accessCookie", new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("jameati_access"))); }
}
