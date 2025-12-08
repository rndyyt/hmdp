package com.hmdp.utils;

import com.hmdp.dto.UserDTO;
import org.jetbrains.annotations.NotNull;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1.查询ThreadLocal
        UserDTO user = UserHolder.getUser();

        // 2.若不存在，则设置状态码并拦截
        if (user == null) {
            response.setStatus(401);
            return false;
        }
        return true;
    }
}
