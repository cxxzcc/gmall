package com.atguigu.gmall.coupon.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.atguigu.gmall.coupon.entity.CouponSpuRelationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券与产品关联
 * 
 * @author wanzenghui
 * @email lemon_wan@aliyun.com
 *
 */
@Mapper
public interface CouponSpuRelationDao extends BaseMapper<CouponSpuRelationEntity> {
	
}
