package com.sun.springmvc.util;

public class SpringTestUtil {


    /**
     * 高效优雅的  首字母大写 工具类
     * @param str
     * @return
     */
    public static String lowerFirstCase(String str){
       char[] chars = str.toCharArray();
       chars[0] += 32;
       return String.valueOf(chars);
    }

}
