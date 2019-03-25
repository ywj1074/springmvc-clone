package com.sun.springmvc.demo.dispatcherservlet;

import com.netflix.ribbon.proxy.annotation.Http;
import com.sun.springmvc.demo.annotation.*;
import com.sun.springmvc.demo.service.WjService;
import com.sun.springmvc.util.SpringTestUtil;
import org.springframework.web.servlet.HandlerMapping;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WjDispatcherServlet extends HttpServlet {


    // 所有的配置信息都存入 properties 中
    private Properties properties = new Properties();

    private List<String> classNames = new ArrayList<>();


    private Map<String,Object> ioc = new HashMap<String,Object>();


    // 写handlerMapping 之前跑一次，写完再跑一次
    //private Map<String, Method> handlerMapping = new HashMap<>();

    private List<Handler> handlerMapping = new ArrayList<>();

    //1.加载配置文件



    @Override
    public void init(ServletConfig config) throws ServletException {

        //1.加载配置文件
        //TODO  找不到资源文件，bug 修改好需要，打开注释
        /*doLoadConfig(config.getInitParameter("contextConfigLocation"));*/
        //2.根据配置文件扫描相关的类

        //TODO  找不到资源文件，bug 修改好需要，打开注释
/*        doScanner(properties.getProperty("scanPackage"));//application.properties文件key*/
        doScanner("com.sun.springmvc.demo");//application.properties文件key

        //3.初始化所有相关的类，并将他们放入IOC容器中
        doInstance();
        //4.实现自动依赖注入
        doAutowried();

        //5.初始化 HandlerMapping
        doInitHandlerMapping();

        //6. 等待运行

        System.out.println("==========WjDispatcherServlet init 方法=============");

    }


    //1.第一步
    private void doLoadConfig(String location){
        System.out.println("----------------"+location);
        InputStream   is = this.getClass().getClassLoader().getResourceAsStream("classresources/application.properties");
         try {
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        }finally {
            if(is != null){
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }




    public void doScanner(String packageName){
        //进行递归扫描,扫描所有的class
        URL url = this.getClass().getResource("/" + packageName.replaceAll("\\.","/"));
        File classDir = new File(url.getFile());
        for(File file : classDir.listFiles()){
            if(file.isDirectory()){
                doScanner(packageName + "." + file.getName());
            } else {
                classNames.add(packageName + "." + file.getName().replace(".class" , ""));
            }
        }
    }



    private  void doInstance(){
        if(classNames.isEmpty()){
            return;
        }
        // 如果不为空，利用反射机制将刚刚扫描进来的类初始化
        try {
            for (String className : classNames){
                Class<?> clazz = Class.forName(className);
                // 接下来进入 bean 的实例化阶段，初始化IOC 容器

                // IOC 容器规则，IOC相当重要
                //1.默认使用类名 首字母小写
                //2.如果用户自定义
                //3. 如果是接口  可以巧用接口类作为key
                if(clazz.isAnnotationPresent(WjController.class)){
                    String beanName = SpringTestUtil.lowerFirstCase(clazz.getSimpleName());
                    try {
                        ioc.put(beanName,clazz.newInstance());//
                    } catch (InstantiationException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                } else if(clazz.isAnnotationPresent(WjMyService.class)){
                    WjMyService service = clazz.getAnnotation(WjMyService.class);
                    String beanName = service.value();
                    //如果不是默认值，就覆盖名字
                    if("".equals(beanName.trim())){
                        beanName = SpringTestUtil.lowerFirstCase(clazz.getSimpleName());
                    }
                    Object instance = clazz.getInterfaces();
                    ioc.put(beanName,instance);
                    Class<?>[] interfaces = clazz.getInterfaces();
                    for (Class<?> i : interfaces){
                        // 将接口类型作为Key
                        ioc.put(i.getName(),instance);
                    }

                } else {
                    continue;
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();

        }

    }





    private void doAutowried(){
        if(ioc.isEmpty()){return;}
        for (Map.Entry<String,Object> entry : ioc.entrySet()){
            // 第一步 获取所有字段 field
            //不管是private 、protected、还是 default 都要强制注入
            Field[] fields = entry.getValue().getClass().getDeclaredFields();
            for (Field field : fields){
                if(!field.isAnnotationPresent(WjAutowired.class)){
                    continue;
                }
                WjAutowired wjAutowired =  field.getAnnotation(WjAutowired.class);

                String beanName = wjAutowired.value().trim();

                if ("".equals(beanName)) {
                    beanName = field.getType().getName();
                }
                //要想访问 到私有的，或者受保护的，我们强制授权访问
                // 类似于 不管愿不愿意 都要强吻
                field.setAccessible(true);
                try {
                    field.set(entry.getValue(),ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                    continue;// 出现异常  我们继续
                }
            }
        }
    }



    private void doInitHandlerMapping(){
        if (ioc.isEmpty()) {return;}
        for (Map.Entry<String,Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(WjController.class)) {
                continue;
            }
            String baseUrl = "";
            //获取Controller的 URL 配置
            if (clazz.isAnnotationPresent(WjRequestMapping.class)){
                WjRequestMapping requestMapping = clazz.getAnnotation(WjRequestMapping.class);
                baseUrl = requestMapping.value();
            }
            //获取Controller的 Method 配置
            Method[] methods = clazz.getMethods();
            for (Method method : methods){
                // 没有加WjRequestMapping 直接忽略
                if (!method.isAnnotationPresent(WjRequestMapping.class)){continue;}
                //映射URL
                WjRequestMapping requestMapping = method.getAnnotation(WjRequestMapping.class);
                String regex = ("/" + baseUrl + requestMapping.value().replaceAll("/+","/"));
                Pattern pattern = Pattern.compile(regex);
//                String url = (baseUrl + requestMapping.value()).replaceAll("/+" , "/");
                handlerMapping.add(new Handler(entry.getValue(),method,pattern));
                System.out.println("Mapping:" + baseUrl + "," + method);


            }
        }


    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }




    //6.等待请求进入
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

//        String url = req.getRequestURI();
//        String contextPath = req.getContextPath();
//        url = url.replace(contextPath,"").replaceAll("/+","/");
//        if (handlerMapping.containsKey(url)){
//            resp.getWriter().write("404 Not Found!");
//        }
//        Method m = handlerMapping.get(url);
//        System.out.println("======================" + m);

        // 到此 可以运行试试看 控制台 日志


        //接下来,//反射。
        // 需要两个参数,第一个 拿到这个method的instance，第二个参数，要拿到实参，从request 中取值
//        m.invoke();
        try {
            doDispatch(req,resp);
        } catch (Exception e){
            resp.getWriter().write("500 Exception...........");
        }


    }

    /**
     * Handler 记录controller中requestMapping和Method的对应关系
     */
    private class Handler{

        protected Object controller;//保存方法对应的实例
        protected Method method;//保存映射的方法
        protected Pattern pattern;
        protected Map<String,Integer> paramIndexMapping;//参数顺序

        /**
         * 构造
         * @param controller
         * @param method
         * @param pattern
         */
        public Handler(Object controller, Method method, Pattern pattern) {
            this.controller = controller;
            this.method = method;
            this.pattern = pattern;
            paramIndexMapping = new HashMap<>();
            putParamIndexMapping(method);
        }
        private void putParamIndexMapping(Method method){
            //提取方法中加了注解的参数
            Annotation [] [] pa = method.getParameterAnnotations();
            for (int i = 0 ;i< pa.length ;i++) {
                for (Annotation a : pa[i]) {
                    if (a instanceof WjRequestParam){
                        String paramName = ((WjRequestParam) a ).value();
                        if (!"".equals(paramName.trim())) {
                            paramIndexMapping.put(paramName,i);
                        }
                    }
                }

            }
            Class<?> [] paramsTypes = method.getParameterTypes();
            for (int i = 0;i<((Class<?>[]) paramsTypes).length; i++) {
                Class<?> type = paramsTypes[i];
                if (type == HttpServletRequest.class || type == HttpServletResponse.class) {
                    paramIndexMapping.put(type.getName(),i);
                }
            }

        }



    }



    public void doDispatch(HttpServletRequest request ,HttpServletResponse response){
        try {

            Handler handler = getHandler(request);
            if (handler == null) {
                //如果没有匹配上，返回404 错误
                try {
                    response.getWriter().write("404 Not Found>>>>>>");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }
            //获取方法的参数列表
            Class<?>[] paramTypes = handler.method.getParameterTypes();

            //保存所有需要自动赋值的参数值
            Object [] paramValues = new Object[paramTypes.length];
            Map<String,String[]> params = request.getParameterMap();
            for (Map.Entry<String,String[]> param : params.entrySet()) {
                String value = Arrays.toString(param.getValue()).replaceAll("\\[|\\]","").replaceAll(",\\s","");
                //如果找到匹配的对象，则开始填充参数值
                if (!handler.paramIndexMapping.containsKey(param.getKey())) {
                    continue;
                }
                int index = handler.paramIndexMapping.get(param.getKey());
                paramValues[index] = convert(paramTypes[index],value);
            }

            // 设置方法中的request 和 response 对象
            int reqIndex = handler.paramIndexMapping.get(HttpServletRequest.class.getName());
            paramValues[reqIndex] = request;
            int respIndex = handler.paramIndexMapping.get(HttpServletResponse.class.getName());
            paramValues[respIndex] = response;


            handler.method.invoke(handler.controller,paramValues);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }



    public Handler getHandler(HttpServletRequest request){

        if (handlerMapping.isEmpty()) {
            return null;
        }
        String url = request.getRequestURI();
        String contextPath = request.getContextPath();
        url = url.replace(contextPath,"").replaceAll("/+" , "/");
        for (Handler handler : handlerMapping) {

            Matcher matcher = handler.pattern.matcher(url);
            //如果没有匹配上继续下一个匹配
            if (!matcher.matches()) {
                continue;
            }
            return handler;
        }

            return null;
    }



    private Object convert(Class<?> type,String value){
        if (Integer.class == type) {
            return Integer.valueOf(value);
        }
        return value;
    }
}
