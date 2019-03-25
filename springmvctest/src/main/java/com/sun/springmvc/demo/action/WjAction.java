package com.sun.springmvc.demo.action;


import com.sun.springmvc.demo.annotation.*;
import com.sun.springmvc.demo.service.WjService;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WjController
@WjRequestMapping("wjAct")
public class WjAction {


    @WjAutowired
    WjService wjService;

    @WjRequestMapping("/query")
    public void query(@WjRequestParam("name") String name , HttpServletRequest request, HttpServletResponse response){

        String result = "my name is:" + name;//wjService.query(name);

        try {
            response.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @WjRequestMapping("/edit")
    public void edit(@WjRequestParam("id") String id , HttpServletRequest request, HttpServletResponse response){

        String result = wjService.query(id);

        try {
            response.getWriter().write(result);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


}
