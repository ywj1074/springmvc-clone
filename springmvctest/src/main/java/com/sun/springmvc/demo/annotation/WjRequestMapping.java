package com.sun.springmvc.demo.annotation;


import java.lang.annotation.*;

@Target( {ElementType.TYPE,ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface WjRequestMapping {

    String value() default "";
}
