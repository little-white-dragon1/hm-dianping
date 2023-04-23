package com.hmdp.intercepter;

import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginIntercepter implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取session
        HttpSession session = request.getSession();
        //从session中获取用户信息
        Object user = session.getAttribute("User");
        //判断user是否为空
        if(user == null){
            response.setStatus(401);
            return false;
        }
        //若user存在
        UserHolder.saveUser((UserDTO) user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //进程结束后释放相关的用户信息，避免信息暴露
        UserHolder.removeUser();
    }
}
