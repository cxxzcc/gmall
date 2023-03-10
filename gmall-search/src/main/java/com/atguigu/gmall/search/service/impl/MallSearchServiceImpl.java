package com.atguigu.gmall.search.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.EsConstant;
import com.atguigu.common.to.es.SkuEsModel;
import com.atguigu.common.utils.R;
import com.atguigu.gmall.search.config.gmallElasticSearch;
import com.atguigu.gmall.search.feign.ProductFeignService;
import com.atguigu.gmall.search.service.MallSearchService;
import com.atguigu.gmall.search.vo.AttrResponseVo;
import com.atguigu.gmall.search.vo.BrandVo;
import com.atguigu.gmall.search.vo.SearchParam;
import com.atguigu.gmall.search.vo.SearchResult;
import org.apache.commons.lang.ArrayUtils;
import org.apache.lucene.search.join.ScoreMode;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.NestedQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.nested.NestedAggregationBuilder;
import org.elasticsearch.search.aggregations.bucket.nested.ParsedNested;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedLongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.ParsedStringTerms;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightField;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MallSearchServiceImpl implements MallSearchService {

    @Autowired
    RestHighLevelClient client;
    @Autowired
    ProductFeignService productFeignService;

    /**
     * ???es??????
     *
     * @param param ?????????????????????
     * @return ???????????????
     */
    public SearchResult search(SearchParam param) {
        SearchResult result = null;
        // 1??????????????????????????????DSL??????

        // 1?????????????????????
        SearchRequest searchRequest = buildSearchRequest(param);
        try {
            // 2?????????????????????
            SearchResponse searchResponse = client.search(searchRequest, gmallElasticSearch.COMMON_OPTIONS);

            // 3????????????????????????????????????????????????
            result = buildSearchResponse(param, searchResponse);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    /**
     * ??????????????????
     * ???????????????????????????????????????????????????????????????????????????????????????????????????????????? ,???????????????????????????????????????????????????????????????
     * <p>
     * 1?????????????????????
     * SearchRequest searchRequest = new SearchRequest("newbank")
     * 2???????????????DSL??????????????????
     * SearchSourceBuilder sourceBuilder = new SearchSourceBuilder()
     * 3?????????????????????
     * QueryBuilder boolQuery = QueryBuilders.boolQuery()
     * QueryBuilder matchQuery = QueryBuilders.matchAllQuery()
     * <p>
     * 4?????????????????????
     * sourceBuilder.sort();
     * sourceBuilder.from();
     * sourceBuilder.size();
     * sourceBuilder.aggregation();
     * sourceBuilder.query(QueryBuilder);
     * sourceBuilder.query(boolQuery);
     * 5?????????????????????
     * searchRequest.source(sourceBuilder);
     */
    private SearchRequest buildSearchRequest(SearchParam param) {
        // ??????DSL????????????
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        /**
         * ?????????????????????????????????????????????????????????????????????????????????
         */
        // 1?????????bool - query
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();

        // 1.1???must????????????
        // ????????????skuTitle
        if (!StringUtils.isEmpty(param.getKeyword())) {
            boolQueryBuilder.must(QueryBuilders.matchQuery("skuTitle", param.getKeyword()));
        }

        // 1.2???filter???????????????
        // ????????????
        if (param.getCatalog3Id() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("catalogId", param.getCatalog3Id()));
        }

        // 1.3?????????
        if (!CollectionUtils.isEmpty(param.getBrandId())) {
            boolQueryBuilder.filter(QueryBuilders.termsQuery("brandId", param.getBrandId()));
        }

        // 1.4?????????
        if (!CollectionUtils.isEmpty(param.getAttrs())) {
            for (String attr : param.getAttrs()) {
                BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
                String[] s = attr.split("_");
                String attrId = s[0];
                String[] attrValues = s[1].split(":");
                // must???????????????????????????????????????boolQuery???????????????
                // ??????boolQuery???????????????must(attrs.attrId)?????????id = 6??????????????????id = 7????????????attr
                boolQuery.must(QueryBuilders.termQuery("attrs.attrId", attrId));
                boolQuery.must(QueryBuilders.termsQuery("attrs.attrValue", attrValues));
                // ????????????????????????????????????????????????
                // ??????????????????????????? ???????????????
                NestedQueryBuilder nestedQuery = QueryBuilders.nestedQuery("attrs", boolQuery, ScoreMode.None);
                boolQueryBuilder.filter(nestedQuery);
            }
        }

        // 1.5?????????
        if (param.getHasStock() != null) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("hasStock", param.getHasStock() == 1));
        }

        // 1.6??????????????? 0_500  _500  500_
        if (!StringUtils.isEmpty(param.getSkuPrice())) {
            RangeQueryBuilder rangeQuery = QueryBuilders.rangeQuery("skuPrice");
            String[] s = param.getSkuPrice().split("_");
            if (s.length == 2) {
                rangeQuery.gte(s[0]).lte(s[1]);
            } else if (s.length == 1) {
                if (param.getSkuPrice().startsWith("_")) {
                    rangeQuery.lte(s[0]);
                }
                if (param.getSkuPrice().endsWith("_")) {
                    rangeQuery.gte(s[0]);
                }
            }
            boolQueryBuilder.filter(rangeQuery);
        }

        // 1???END ??????????????????
        sourceBuilder.query(boolQueryBuilder);

        /**
         * ????????????????????????
         */
        // 2.1?????????
        if (!StringUtils.isEmpty(param.getSort())) {
            String[] s = param.getSort().split("_");
            SortOrder order = s[1].equalsIgnoreCase("asc") ? SortOrder.ASC : SortOrder.DESC;
            sourceBuilder.sort(s[0], order);
        }

        // 2.2?????????
        sourceBuilder.from((param.getPageNum() - 1) * EsConstant.PRODUCT_PAGESIZE);
        sourceBuilder.size(EsConstant.PRODUCT_PAGESIZE);

        // 2.3?????????
        if (!StringUtils.isEmpty(param.getKeyword())) {
            HighlightBuilder highlightBuilder = new HighlightBuilder();
            highlightBuilder.field("skuTitle");
            highlightBuilder.preTags("<b style='color:red'>");
            highlightBuilder.postTags("</b>");
            sourceBuilder.highlighter(highlightBuilder);
        }

        /**
         * ???????????????????????????????????????????????????????????????
         */
        // 3.1???????????????
        TermsAggregationBuilder brand_agg = AggregationBuilders.terms("brand_agg").field("brandId").size(10);
        // ????????????????????????name?????????
        brand_agg.subAggregation(AggregationBuilders.terms("brand_name_agg").field("brandName").size(1));
        brand_agg.subAggregation(AggregationBuilders.terms("brand_img_agg").field("brandImg").size(1));
        // ??????DSL
        // TODO ????????????
        sourceBuilder.aggregation(brand_agg);

        // 3.2???????????????
        TermsAggregationBuilder catalog_agg = AggregationBuilders.terms("catalog_agg").field("catalogId").size(20);
        // ???????????????????????????
        catalog_agg.subAggregation(AggregationBuilders.terms("catalog_name_agg").field("catalogName").size(1));
        // ??????DSL
        // TODO ????????????
        sourceBuilder.aggregation(catalog_agg);

        // 3.3??????????????????????????????
        NestedAggregationBuilder attr_agg = AggregationBuilders.nested("attr_agg", "attrs");
        // ??????id??????
        TermsAggregationBuilder attr_id_agg = AggregationBuilders.terms("attr_id_agg").field("attrs.attrId").size(10);
        // ?????????=??????id??????????????????????????? attr_name??????
        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_name_agg").field("attrs.attrName").size(1));
        // ?????????=??????id??????????????????????????? attr_value??????
        attr_id_agg.subAggregation(AggregationBuilders.terms("attr_value_agg").field("attrs.attrValue").size(50));
        // ???????????????
        attr_agg.subAggregation(attr_id_agg);
        // ??????DSL
        // TODO ??????????????????
        sourceBuilder.aggregation(attr_agg);

        SearchRequest searchRequest = new SearchRequest(new String[]{EsConstant.PRODUCT_INDEX}, sourceBuilder);

        // ??????DSL
        System.out.println(sourceBuilder.toString());
        return searchRequest;
    }

    /**
     * ??????????????????
     * 1?????????????????????????????????
     * 2???????????????????????????????????????????????????
     * 3???????????????????????????????????????????????????
     * 4???????????????????????????????????????????????????
     * 5???????????????   pageNum:????????????  total:????????????    totalPages: ?????????
     */
    private SearchResult buildSearchResponse(SearchParam param, SearchResponse searchResponse) {
        SearchResult result = new SearchResult();
        SearchHits hits = searchResponse.getHits();
        // 1?????????????????????????????????
        List<SkuEsModel> products = new ArrayList<>();
        if (!ArrayUtils.isEmpty(hits.getHits())) {
            for (SearchHit hit : hits.getHits()) {
                String jsonStr = hit.getSourceAsString();
                SkuEsModel model = JSON.parseObject(jsonStr, SkuEsModel.class);
                // ??????????????????
                if (!StringUtils.isEmpty(param.getKeyword())) {
                    HighlightField skuTitle = hit.getHighlightFields().get("skuTitle");
                    model.setSkuTitle(skuTitle.getFragments()[0].string());
                }
                products.add(model);
            }
        }
        result.setProducts(products);

        // 2???????????????????????????????????????????????????
        List<SearchResult.AttrVo> attrVos = new ArrayList<>();
        ParsedNested attr_agg = searchResponse.getAggregations().get("attr_agg");
        ParsedLongTerms attr_id_agg = attr_agg.getAggregations().get("attr_id_agg");
        List<? extends Terms.Bucket> attrBuckets = attr_id_agg.getBuckets();
        for (Terms.Bucket bucket : attrBuckets) {
            SearchResult.AttrVo attrVo = new SearchResult.AttrVo();
            // ????????????ID
            attrVo.setAttrId(bucket.getKeyAsNumber().longValue());
            // ??????????????????
            ParsedStringTerms attr_name_agg = bucket.getAggregations().get("attr_name_agg");
            attrVo.setAttrName(attr_name_agg.getBuckets().get(0).getKeyAsString());
            // ??????????????????
            ParsedStringTerms attr_value_agg = bucket.getAggregations().get("attr_value_agg");
            List<String> attrValues = attr_value_agg.getBuckets().stream().map(item -> {
                return ((Terms.Bucket) item).getKeyAsString();
            }).collect(Collectors.toList());
            attrVo.setAttrValue(attrValues);
            attrVos.add(attrVo);
        }
        result.setAttrs(attrVos);


        // 3???????????????????????????????????????????????????
        List<SearchResult.BrandVo> brandVos = new ArrayList<>();
        ParsedLongTerms brand_agg = searchResponse.getAggregations().get("brand_agg");
        List<? extends Terms.Bucket> brandBuckets = brand_agg.getBuckets();
        for (Terms.Bucket bucket : brandBuckets) {
            SearchResult.BrandVo brandVo = new SearchResult.BrandVo();
            // ????????????ID
            brandVo.setBrandId(bucket.getKeyAsNumber().longValue());
            // ??????????????????
            ParsedStringTerms brand_name_agg = bucket.getAggregations().get("brand_name_agg");
            brandVo.setBrandName(brand_name_agg.getBuckets().get(0).getKeyAsString());
            // ??????????????????
            ParsedStringTerms brand_img_agg = bucket.getAggregations().get("brand_img_agg");
            brandVo.setBrandImg(brand_img_agg.getBuckets().get(0).getKeyAsString());
            brandVos.add(brandVo);
        }
        result.setBrands(brandVos);

        // 4???????????????????????????????????????????????????
        List<SearchResult.CatalogVo> catalogVos = new ArrayList<>();
        ParsedLongTerms catalog_agg = searchResponse.getAggregations().get("catalog_agg");
        List<? extends Terms.Bucket> catalogBuckets = catalog_agg.getBuckets();
        for (Terms.Bucket bucket : catalogBuckets) {
            SearchResult.CatalogVo catalogVo = new SearchResult.CatalogVo();
            // ????????????Id
            catalogVo.setCatalogId(Long.parseLong(bucket.getKeyAsString()));
            // ??????????????????
            ParsedStringTerms catalog_name_agg = bucket.getAggregations().get("catalog_name_agg");
            catalogVo.setCatalogName(catalog_name_agg.getBuckets().get(0).getKeyAsString());
            catalogVos.add(catalogVo);
        }
        result.setCatalogs(catalogVos);
        // 5???????????????   pageNum:???????????? ???total:???????????? ???totalPages: ?????????
        long total = hits.getTotalHits().value;
        int totalPages = (int) total % EsConstant.PRODUCT_PAGESIZE == 0 ? (int) total / EsConstant.PRODUCT_PAGESIZE : ((int) total / EsConstant.PRODUCT_PAGESIZE + 1);
        result.setPageNum(param.getPageNum());
        result.setTotal(total);
        result.setTotalPages(totalPages);

        // 6??????????????????
        List<Integer> pageNavs = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            pageNavs.add(i);
        }
        result.setPageNavs(pageNavs);

        // 7??????????????????????????????
        if (param.getAttrs() != null && param.getAttrs().size() > 0) {
            List<SearchResult.NavVo> collect = param.getAttrs().stream().map(attr -> {
                //1??????????????????attrs?????????????????????
                SearchResult.NavVo navVo = new SearchResult.NavVo();
                // attrs=2_5???:6???
                String[] s = attr.split("_");
                navVo.setNavValue(s[1]);
                R r = productFeignService.attrInfo(Long.parseLong(s[0]));
                // ??????????????????????????? ????????????Id??????????????????????????????????????????????????????????????????????????????????????????
                result.getAttrIds().add(Long.parseLong(s[0]));
                if (r.getCode() == 0) {
                    AttrResponseVo data = r.getData("attr", new TypeReference<AttrResponseVo>() {
                    });
                    // ???????????????
                    navVo.setNavName(data.getAttrName());
                } else {
                    navVo.setNavName(s[0]);
                }

                //2???????????????????????????????????????????????????????????????????????????????????????url?????????????????????
                //??????????????????????????????????????????
                String replace = replaceQueryString(param, attr, "attrs");
                navVo.setLink("http://search.gmall.com/list.html?" + replace);

                return navVo;
            }).collect(Collectors.toList());

            result.setNavs(collect);
        }

        // ???????????????
        if (param.getBrandId() != null && param.getBrandId().size() > 0) {
            List<SearchResult.NavVo> navs = result.getNavs();
            SearchResult.NavVo navVo = new SearchResult.NavVo();
            navVo.setNavName("??????");
            // TODO ????????????????????????
            R r = productFeignService.brandsInfo(param.getBrandId());
            if (r.getCode() == 0) {
                List<BrandVo> brand = r.getData("brand", new TypeReference<List<BrandVo>>() {
                });
                StringBuffer sb = new StringBuffer();
                String replace = "";
                for (BrandVo brandVo : brand) {
                    sb.append(brandVo.getName()+";");
                    replace = replaceQueryString(param, brandVo.getBrandId()+"", "brandId");
                }
                navVo.setNavValue(sb.toString());
                navVo.setLink("http://search.gmall.com/list.html?" + replace);
            }
            navs.add(navVo);
        }

        // TODO ?????? ?????????


        return result;
    }

    private String replaceQueryString(SearchParam param, String value, String key) {
        String encode = null;
        try {
            encode = URLEncoder.encode(value, "UTF-8");
            encode = encode.replace("+", "%20");  //??????????????????????????????Java???????????????????????????
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        // ????????????X??????????????????????????????
        // ????????????????????????attrs???????????????????????????????????????????????? ??????&??????
        // TODO BUG????????????????????????&
        return param.get_queryString().replace("&"+ key + "=" + encode, "");
    }

}