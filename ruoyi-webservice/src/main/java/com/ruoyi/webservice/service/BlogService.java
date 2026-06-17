package com.ruoyi.webservice.service;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebService;

@WebService(name = "MybBlog",  // 与接口中指定的name一致
        targetNamespace = "http://blogService.service.myb.blog.com" // 与接口中的命名空间一致,一般是接口的包名倒
)
public interface BlogService {

    @WebMethod
    public String send(@WebParam(name = "username") String username);

    @WebMethod
    public String message(@WebParam(name = "message") String message);

}

