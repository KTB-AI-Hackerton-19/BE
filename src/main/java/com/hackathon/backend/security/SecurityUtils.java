package com.hackathon.backend.security;

import com.hackathon.backend.exception.CustomException;
import com.hackathon.backend.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return (String) authentication.getPrincipal();
    }
}
