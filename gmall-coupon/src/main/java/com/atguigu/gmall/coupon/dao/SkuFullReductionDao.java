package com.atguigu.gmall.coupon.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.atguigu.gmall.coupon.entity.SkuFullReductionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品满减信息
 * 
 * @author wanzenghui
 * @email lemon_wan@aliyun.com
 *
 */
@Mapper
public interface SkuFullReductionDao extends BaseMapper<SkuFullReductionEntity> {
	
}
