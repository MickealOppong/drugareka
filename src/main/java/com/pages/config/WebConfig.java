package com.pages.config;

import com.pages.impl.AuditAwareImpl;
import com.pages.util.MediaStorageLocation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.web.client.RestClient;
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


    private final MediaStorageLocation mediaStorageLocation;

    public WebConfig(MediaStorageLocation mediaStorageLocation) {
        this.mediaStorageLocation = mediaStorageLocation;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = mediaStorageLocation.getRoot()
                .toUri()
                .toString();

        registry.addResourceHandler("/Media/**")
                .addResourceLocations(location);
    }

    @Bean
    public MessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource =
                new ReloadableResourceBundleMessageSource();

        messageSource.setBasename("classpath:Messages/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);

        return messageSource;
    }

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }


}
