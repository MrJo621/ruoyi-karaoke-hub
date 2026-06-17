package com.ruoyi.webservice.service.impl;

import com.ruoyi.webservice.service.BlogService;
import org.springframework.stereotype.Component;

import javax.jws.WebService;

@Component
@WebService(name = "MybBlog",  // 与接口中指定的name一致
        targetNamespace = "http://blogService.service.myb.blog.com", // 与接口中的命名空间一致,一般是接口的包名倒
        endpointInterface = "com.ruoyi.webservice.service.BlogService"// 接口地址
)
public class BlogServiceImpl implements BlogService {
    @Override
    public String send(String username) {
        if ("zhangsan".equals(username)) {
            return "张三";
        }
        return "李四，王五";
    }

    @Override
    public String message(String message) {
        return "====Hello ====WebServer===" + message;
    }
}

