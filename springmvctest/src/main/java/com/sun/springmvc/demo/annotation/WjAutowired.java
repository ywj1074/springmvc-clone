package com.sun.springmvc.demo.annotation;


import java.lang.annotation.*;

@Target( {ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WjAutowired {
    String value() default "";
}
