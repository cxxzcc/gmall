package com.atguigu.gmall.coupon.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.atguigu.gmall.coupon.entity.SkuLadderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品阶梯价格
 * 
 * @author wanzenghui
 * @email lemon_wan@aliyun.com
 *
 */
@Mapper
public interface SkuLadderDao extends BaseMapper<SkuLadderEntity> {
	
}
