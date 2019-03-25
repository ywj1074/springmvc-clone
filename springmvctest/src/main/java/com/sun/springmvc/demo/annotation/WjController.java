package com.sun.springmvc.demo.annotation;


import java.lang.annotation.*;

@Target( {ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WjController {
    String value() default "";
}
