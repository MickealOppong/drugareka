package com.pages.interceptors;

import com.pages.enums.Visibility;
import com.pages.exception.InsufficientPublicPresenceException;
import com.pages.model.AppUser;
import com.pages.repository.AppUserRepo;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
@Slf4j
@Aspect
@Component
public class PublicPostEnforcementAspect {



}

