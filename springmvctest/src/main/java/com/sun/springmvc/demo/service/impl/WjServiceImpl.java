package com.sun.springmvc.demo.service.impl;

import com.sun.springmvc.demo.annotation.WjMyService;
import com.sun.springmvc.demo.service.WjService;

@WjMyService
public class WjServiceImpl implements WjService {

    @Override
    public String query(String name) {
        return "my name is" + name;
    }
}
