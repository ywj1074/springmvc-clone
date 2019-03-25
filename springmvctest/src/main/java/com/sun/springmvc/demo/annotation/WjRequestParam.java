package com.sun.springmvc.demo.annotation;


import java.lang.annotation.*;

@Target( {ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WjRequestParam {
    String value() default "";
}
