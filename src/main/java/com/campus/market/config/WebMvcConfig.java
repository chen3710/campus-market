package com.campus.market.config;

import com.campus.market.auth.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * Web 层配置：注册 JWT 拦截器、上传文件的静态资源映射、跨域规则。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AppProperties properties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor).addPathPatterns("/api/**");
    }

    /**
     * 把本地上传目录暴露为可访问的静态资源，前端 <img src="/uploads/xxx.png"> 即可直接显示。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadDir = Paths.get(properties.upload().dir())
                .toAbsolutePath()
                .normalize()
                .toString()
                .replace('\\', '/');
        registry.addResourceHandler(properties.upload().urlPrefix() + "/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }

    /**
     * 前后端分离部署时（例如前端跑在 5173 端口）需要跨域支持。
     * 这里用 allowedOriginPatterns 而不是 allowedOrigins("*")，
     * 因为 allowCredentials(true) 与通配符 Origin 不能同时生效。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
