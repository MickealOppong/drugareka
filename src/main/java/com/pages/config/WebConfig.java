package com.pages.config;

import com.pages.impl.AuditAwareImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;


@EnableWebSecurity
@EnableMethodSecurity(securedEnabled = true)
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditAware")
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig implements WebMvcConfigurer {

    @Bean
    public AuditorAware<String> auditAware(){
        return  new AuditAwareImpl();
    }


    @Value("${MEDIA_UPLOAD_DIR:./local-media/}") // Falls back to a local folder when running on your computer
    private String mediaUploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String path = "file:" + mediaUploadDir;
        registry.addResourceHandler("/Media/**")
                .addResourceLocations(path);
    }
}
