package com.atguigu.gmall.product.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.ProductConstant;
import com.atguigu.common.to.SkuHasStockTo;
import com.atguigu.common.to.SkuReductionTo;
import com.atguigu.common.to.SpuBoundTo;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;
import com.atguigu.common.utils.R;
import com.atguigu.gmall.product.dao.SpuInfoDao;
import com.atguigu.gmall.product.entity.*;
import com.atguigu.gmall.product.feign.CouponFeignService;
import com.atguigu.gmall.product.feign.SearchFeignService;
import com.atguigu.gmall.product.feign.WareFeignService;
import com.atguigu.gmall.product.service.*;
import com.atguigu.gmall.product.vo.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Service("spuInfoService")
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    SpuInfoDescService descService;
    @Autowired
    SpuImagesService imagesService;
    @Autowired
    AttrService attrService;
    @Autowired
    ProductAttrValueService valueService;
    @Autowired
    SkuInfoService skuInfoService;
    @Autowired
    SkuImagesService skuImagesService;
    @Autowired
    SkuSaleAttrValueService skuSaleAttrValueService;
    @Autowired
    CouponFeignService couponFeignService;
    @Autowired
    BrandService brandService;
    @Autowired
    CategoryService categoryService;
    @Autowired
    WareFeignService wareFeignService;
    @Autowired
    SearchFeignService searchFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }

    // TODO ??????????????????????????????????????????????????????????????????
    @Transactional
    @Override
    public void saveSpuInfo(SpuSaveVo vo) {
        //1?????????spu????????????   pms_spu_info
        //2?????????spu???????????????  pms_spu_info_desc
        //3?????????spu????????????   pms_spu_images
        //4?????????spu???????????????; pms_product_attr_value
        //5?????????spu???????????????;gmall_sms-???ms_spu_bounds????????????
        //6???????????????spu???????????????sku??????;
        //6.1)???sku????????????.???;pms_sku_info
        //6.2)???sku???????????????;pms_sku_images
        //6.3)???sku??????????????????.???:pms_sku_sale_attr_value
        //6.4)???sku???????????????????????????;gmall_sms-???sms_sku_ladder\sms_sku_full_reduction\sms_member_price????????????

        //1?????????spu????????????   pms_spu_info
        SpuInfoEntity spuInfoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties(vo, spuInfoEntity);
        spuInfoEntity.setCreateTime(new Date());
        spuInfoEntity.setUpdateTime(new Date());
        this.saveBaseSpuInfo(spuInfoEntity);

        //2?????????spu???????????????  pms_spu_info_desc
        List<String> decript = vo.getDecript();
        SpuInfoDescEntity descEntity = new SpuInfoDescEntity();
        descEntity.setSpuId(spuInfoEntity.getId());
        descEntity.setDecript(String.join(",", decript));
        descService.saveSpuInfoDesc(descEntity);

        //3?????????spu????????????   pms_spu_images
        List<String> images = vo.getImages();
        imagesService.saveImages(spuInfoEntity.getId(), images);

        //4?????????spu???????????????; pms_product_attr_value
        List<BaseAttrs> baseAttrs = vo.getBaseAttrs();
        List<ProductAttrValueEntity> collect = baseAttrs.stream().map(attr -> {
            ProductAttrValueEntity valueEntity = new ProductAttrValueEntity();
            valueEntity.setAttrId(attr.getAttrId());
            valueEntity.setAttrName(attrService.getById(attr.getAttrId()).getAttrName());
            valueEntity.setAttrValue(attr.getAttrValues());
            valueEntity.setQuickShow(attr.getShowDesc());
            valueEntity.setSpuId(spuInfoEntity.getId());
            return valueEntity;
        }).collect(Collectors.toList());
        valueService.saveProductAttr(collect);

        //5?????????spu???????????????;gmall_sms-???ms_spu_bounds????????????
        Bounds bounds = vo.getBounds();
        SpuBoundTo spuBoundTo = new SpuBoundTo();
        BeanUtils.copyProperties(bounds, spuBoundTo);
        spuBoundTo.setSpuId(spuInfoEntity.getId());
        R r = couponFeignService.saveSpuBounds(spuBoundTo);
        if (r.getCode() != 0) {
            log.error("????????????spu??????????????????");
        }

        //6???????????????spu???????????????sku??????;
        List<Skus> skus = vo.getSkus();
        if (!CollectionUtils.isEmpty(skus)) {
            skus.forEach(item -> {
                String defaultImg = "";
                for (Images image : item.getImages()) {
                    if (image.getDefaultImg() == 1) {
                        defaultImg = image.getImgUrl();
                    }
                }
                // ????????????????????????????????????
                // private String skuName;
                // private BigDecimal price;
                // private String skuTitle;
                // private String skuSubtitle;
                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                BeanUtils.copyProperties(item, skuInfoEntity);
                skuInfoEntity.setBrandId(spuInfoEntity.getBrandId());
                skuInfoEntity.setCatalogId(spuInfoEntity.getCatalogId());
                skuInfoEntity.setSaleCount(0L);
                skuInfoEntity.setSpuId(spuInfoEntity.getId());
                skuInfoEntity.setSkuDefaultImg(defaultImg);
                //6.1)???sku????????????.???;pms_sku_info
                skuInfoService.saveSkuInfo(skuInfoEntity);

                Long skuId = skuInfoEntity.getSkuId();
                List<SkuImagesEntity> skuImagesEntities = item.getImages().stream().map(image -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(image.getImgUrl());
                    skuImagesEntity.setDefaultImg(image.getDefaultImg());
                    return skuImagesEntity;
                }).filter(entity -> {
                    // ??????false????????????
                    return !StringUtils.isEmpty(entity.getImgUrl());
                }).collect(Collectors.toList());
                //6.2)???sku???????????????;pms_sku_images
                skuImagesService.saveBatch(skuImagesEntities);
                // TODO ?????????????????????????????????
                List<Attr> attrs = item.getAttr();
                List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = attrs.stream().map(attr -> {
                    SkuSaleAttrValueEntity valueEntity = new SkuSaleAttrValueEntity();
                    BeanUtils.copyProperties(attr, valueEntity);
                    valueEntity.setSkuId(skuId);
                    return valueEntity;
                }).collect(Collectors.toList());
                //6.3)???sku??????????????????.???:pms_sku_sale_attr_value
                skuSaleAttrValueService.saveBatch(skuSaleAttrValueEntities);

                //6.4)???sku???????????????????????????;gmall_sms->
                // sms_sku_ladder
                // sms_sku_full_reduction
                // sms_member_price
                SkuReductionTo skuReductionTo = new SkuReductionTo();
                // TODO ???????????????????????????[????????????????????????????????????????????????]
                // ?????? ????????????<=0 ????????????<=0 ??????
                //if (item.getFullCount() > 0 || new BigDecimal(0).compareTo(item.getFullPrice()) == -1 || !StringUtils.isEmpty(item.getMemberPrice())) {
                BeanUtils.copyProperties(item, skuReductionTo);
                skuReductionTo.setSkuId(skuId);
                R r1 = couponFeignService.saveSkuReduction(skuReductionTo);
                if (r1.getCode() != 0) {
                    log.error("????????????sku??????????????????");
                }

            });
        }
    }

    /**
     * ??????SPU????????????
     *
     * @param spuInfoEntity
     */
    @Override
    public void saveBaseSpuInfo(SpuInfoEntity spuInfoEntity) {
        this.baseMapper.insert(spuInfoEntity);
    }

    /**
     * SPU??????
     * @param params
     * @return
     */
    @Override
    public PageUtils queryPageByCondition(Map<String, Object> params) {
        QueryWrapper<SpuInfoEntity> queryWrapper = new QueryWrapper<>();
        String key = (String) params.get("key");
        if (!StringUtils.isEmpty(key)) {
            queryWrapper.and(qw->{
                qw.eq("id", key).or().like("spu_name", key);
            });
        }
        //status: 0
        //brandId: 3
        //catelogId: 225
        String status = (String) params.get("status");
        if (!StringUtils.isEmpty(status)) {
            queryWrapper.eq("publish_status", status);
        }
        String brandId = (String) params.get("brandId");
        if (!StringUtils.isEmpty(brandId) && !"0".equalsIgnoreCase(brandId)) {
            queryWrapper.eq("brand_Id", brandId);
        }
        String catelogId = (String) params.get("catelogId");
        if (!StringUtils.isEmpty(catelogId) && !"0".equalsIgnoreCase(catelogId)) {
            queryWrapper.eq("catalog_Id", catelogId);
        }

        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                queryWrapper
        );
        return new PageUtils(page);
    }

    /**
     * ???????????????spu??????????????????????????????sku?????????es??????skuId????????????id???
     */
    @Override
    public void up(Long spuId) {
        // sku??????????????????spu??????????????????????????????sku???????????????????????????????????????????????????
        // TODO 4???????????????sku????????????????????? ???????????????
        // 1?????????spuId????????????Attr???????????????????????????Attr
        List<ProductAttrValueEntity> baseAttrs = valueService.baseAttrlistforspu(spuId);
        // 2?????????????????????AttrId??????
        List<Long> attrIds = baseAttrs.stream().map(attr -> {
            return attr.getAttrId();
        }).collect(Collectors.toList());
        // 3???????????????????????????AttrId??????????????????set
        List<Long> searchAttrIds = attrService.selectSearchAttrIds(attrIds);
        HashSet<Long> idSet = new HashSet<>(searchAttrIds);
        // 4????????????Attr??????set??????????????????List<SkuEsModel.Attrs>???????????????es
        List<SkuEsModel.Attrs> attrsList = baseAttrs.stream().filter(item -> {
            return idSet.contains(item.getAttrId());
        }).map(item -> {
            SkuEsModel.Attrs attrs = new SkuEsModel.Attrs();
            BeanUtils.copyProperties(item, attrs);
            return attrs;
        }).collect(Collectors.toList());

        // 6???????????????spuId???????????????sku????????????
        List<SkuInfoEntity> skus = skuInfoService.getSkusBySpuId(spuId);
        // 7???????????????skuId
        List<Long> skuIds = skus.stream().map(SkuInfoEntity::getSkuId).collect(Collectors.toList());
        // TODO 1??????????????????????????????????????????
        // 8???????????????????????????null????????????true
        Map<Long, Boolean> stockMap = null;
        try {
            // 9??????????????????????????????sku???????????????
            R r = wareFeignService.getSkusHasStock(skuIds);
            // 10?????????????????????????????????????????????????????????????????????
            TypeReference<List<SkuHasStockTo>> typeReference = new TypeReference<List<SkuHasStockTo>>() {
            };
            // 11????????????skuId???key???hasStock??????
            stockMap = r.getData(typeReference).stream().collect(Collectors.toMap(SkuHasStockTo::getSkuId, item -> item.getHasStock()));
        }catch (Exception e){
            log.error("?????????????????????????????????{}", e);
        }
        Map<Long, Boolean> finalStockMap = stockMap;
        // 12???????????????sku????????????List<SkuEsModel>?????????es
        List<SkuEsModel> upProducts = skus.stream().map(sku -> {
            SkuEsModel esModel = new SkuEsModel();
            BeanUtils.copyProperties(sku, esModel);
            // ????????????????????????
            // skuPrice, skuImg, hasstock, hotScore,
            esModel.setSkuPrice(sku.getPrice());
            esModel.setSkuImg(sku.getSkuDefaultImg());
            // ?????????null?????????????????????????????????true???
            if (finalStockMap == null) {
                esModel.setHasStock(true);
            }else {
                esModel.setHasStock(finalStockMap.get(sku.getSkuId()));
            }
            // TODO 2??????????????????0
            esModel.setHotScore(0l);
            // TODO 3?????????????????????????????????
            BrandEntity brand = brandService.getById(esModel.getBrandId());
            esModel.setBrandName(brand.getName());
            esModel.setBrandImg(brand.getLogo());

            CategoryEntity category = categoryService.getById(esModel.getCatalogId());
            esModel.setCatalogName(category.getName());
            // ????????????????????????????????????spu?????????sku??????
            esModel.setAttrs(attrsList);
            return esModel;
        }).collect(Collectors.toList());

        // TODO 5?????????????????????es???????????????gmall-search
        R r = searchFeignService.productStatusUp(upProducts);
        if (r.getCode() == 0) {
            // ??????????????????
            // TODO 6???????????????spu????????? ??????
            baseMapper.updateSpuStatus(spuId, ProductConstant.StatusEnum.SPU_UP.getCode());
        }else {
            // ??????????????????
            // TODO 7???????????????????????????????????????????????????
            //Feign????????????
            /**
             * 1???????????????????????????????????????json
             *      RequestTemplate template = buildTemplateFromArgs.create(argv);
             * 2?????????????????????????????????????????????????????????????????????
             *      excuteAndDecode(template)
             * 3?????????????????????????????????
             *      while(true){
             *          try{
             *              excuteAndDecode(template)
             *          }catch() {
             *              try{
             *                  // ????????????5?????????????????????
             *                  retryer.continueOrPropagate(e);
             *               }catch() {
             *                  throw ex;
             *               }
             *              continue;
             *          }
             *      }
             *
             *
             */
        }
    }

    /**
     * ??????skuId??????spu?????????
     * @param skuId
     * @return
     */
    @Override
    public SpuInfoEntity getSpuInfoBySkuId(Long skuId) {

        //?????????sku???????????????
        SkuInfoEntity skuInfoEntity = skuInfoService.getById(skuId);

        //??????spuId
        Long spuId = skuInfoEntity.getSpuId();

        //?????????spuId??????spuInfo?????????????????????
        SpuInfoEntity spuInfoEntity = baseMapper.selectById(spuId);

        //???????????????????????????????????????
        BrandEntity brandEntity = brandService.getById(spuInfoEntity.getBrandId());
        spuInfoEntity.setBrandName(brandEntity.getName());

        return spuInfoEntity;
    }
}