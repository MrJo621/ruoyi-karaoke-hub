package com.ruoyi.framework.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.model.LoginUser;
import org.jeecg.modules.jmreport.api.JmReportTokenServiceI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Acechengui
 * <p>
 * 自定义报表鉴权(如果不进行自定义，则所有请求不做权限控制)
 */
@Component
public class JimuReportTokenService implements JmReportTokenServiceI {

    @Autowired
    private TokenService tokenService;

    // 令牌自定义标识
    @Value("${token.header}")
    private String tokenHeader;

    /**
     * 获取登录人用户名
     */
    @Override
    public String getUsername(String s) {
        LoginUser loginUser = tokenService.getLoginUser(s);
        return loginUser.getUsername();
    }

    @Override
    public String[] getRoles(String s) {
        return tokenService.getLoginUser(s).getUser().getRoles().stream().map(a -> a.getRoleId().toString()).collect(Collectors.toList()).toArray(new String[5]);
    }

    /**
     * Token校验
     */
    @Override
    public Boolean verifyToken(String s) {
        if (s != null && !s.isEmpty()) {
            LoginUser loginUser = tokenService.getLoginUser(s);
            return loginUser != null;
        }
        return false;
    }

    @Override
    public Map<String, Object> getUserInfo(String token) {
        LoginUser loginUser = tokenService.getLoginUser(token);
        return new ObjectMapper().convertValue(loginUser,Map.class);
    }


    @Override
    public HttpHeaders customApiHeader() {
        HttpHeaders header = new HttpHeaders();
        header.add(tokenHeader, getToken());
        header.add("X-Access-Token", getToken());
        return header;
    }



}
