package com.atguigu.gmall.coupon.config;


import org.springframework.context.annotation.Configuration;

/**
 * @Description: 自定义阻塞返回方法
 * @Created: with IntelliJ IDEA.
 * @author: wanzenghui
 *
 **/

@Configuration
public class gmallCouponSentinelConfig {

//    public gmallCouponSentinelConfig() {
//
//        WebCallbackManager.setUrlBlockHandler(new UrlBlockHandler() {
//            @Override
//            public void blocked(HttpServletRequest request, HttpServletResponse response, BlockException ex) throws IOException {
//                R error = R.error(BizCodeEnum.TO_MANY_REQUEST.getCode(), BizCodeEnum.TO_MANY_REQUEST.getMessage());
//                response.setCharacterEncoding("UTF-8");
//                response.setContentType("application/json");
//                response.getWriter().write(JSON.toJSONString(error));
//
//            }
//        });
//
//    }

}
