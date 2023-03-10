package com.atguigu.gmall.member.service;

import com.atguigu.common.utils.PageUtils;
import com.atguigu.gmall.member.entity.MemberEntity;
import com.atguigu.gmall.member.exception.PhoneException;
import com.atguigu.gmall.member.exception.UsernameException;
import com.atguigu.gmall.member.vo.MemberUserLoginVo;
import com.atguigu.gmall.member.vo.MemberUserRegisterVo;
import com.atguigu.gmall.member.vo.SocialUser;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Map;

/**
 * 会员
 *
 * @author wanzenghui
 * @email lemon_wan@aliyun.com
 * @date 2020-08-02 15:18:09
 */
public interface MemberService extends IService<MemberEntity> {

    PageUtils queryPage(Map<String, Object> params);

    /**
     * 用户注册
     * @param vo
     */
    void register(MemberUserRegisterVo vo);

    /**
     * 判断邮箱是否重复
     * @param phone
     * @return
     */
    void checkPhoneUnique(String phone) throws PhoneException;

    /**
     * 判断用户名是否重复
     * @param userName
     * @return
     */
    void checkUserNameUnique(String userName) throws UsernameException;

    /**
     * 用户登录
     */
    MemberEntity login(MemberUserLoginVo vo);

    /**
     * 社交用户的登录
     * @param socialUser
     * @return
     */
    MemberEntity login(SocialUser socialUser) throws Exception;

//    /**
//     * 微信登录
//     * @param accessTokenInfo
//     * @return
//     */
//    MemberEntity login(String accessTokenInfo);
}

