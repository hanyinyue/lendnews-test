package com.heima.wemedia.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.injector.methods.SelectById;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.common.constants.WemediaConstants;
import com.heima.common.constants.WmNewsMessageConstants;
import com.heima.common.exception.CustomException;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.model.wemedia.pojos.WmNews;
import com.heima.model.wemedia.pojos.WmNewsMaterial;
import com.heima.model.wemedia.pojos.WmUser;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.mapper.WmNewsMapper;
import com.heima.wemedia.mapper.WmNewsMaterialMapper;
import com.heima.wemedia.service.WmNewsAutoScanService;
import com.heima.wemedia.service.WmNewsService;
import com.heima.wemedia.service.WmNewsTaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional
public class WmNewsServiceImpl extends ServiceImpl<WmNewsMapper, WmNews> implements WmNewsService {

    /**
     * 查询所有自媒体文章
     * @param dto
     * @return
     */
    @Override
    public ResponseResult findAll(WmNewsPageReqDto dto) {
        //1.检查参数
        if(dto == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        dto.checkParam();
        //获取当前线程对象
        WmUser user = WmThreadLocalUtil.getUser();
        if (user==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.NEED_LOGIN);
        }
        IPage<WmNews> page = new Page<>();

        LambdaQueryWrapper<WmNews> lqw = new LambdaQueryWrapper<>();
        //根据状态查询 全部 待审核 草稿等
        lqw.eq(dto.getStatus()!=null,WmNews::getStatus,dto.getStatus());
        //根据频道查询
        lqw.eq(dto.getChannelId()!=null,WmNews::getChannelId,dto.getChannelId());
        //根据关键字查询
        lqw.like(dto.getKeyword()!=null,WmNews::getTitle,dto.getKeyword());
        //根据开始时间和结束时间查询
        if (dto.getBeginPubDate()!=null && dto.getEndPubDate()!=null) {
            lqw.between(WmNews::getPublishTime,dto.getBeginPubDate(),dto.getEndPubDate());
        }
        //查询当前登录用户的文章
        lqw.eq(WmNews::getUserId,user.getId());

        //发布时间倒序查询
        lqw.orderByDesc(WmNews::getCreatedTime);

        page = page(page,lqw);

        //3.结果返回
        ResponseResult responseResult = new PageResponseResult(dto.getPage(),dto.getSize(),(int)page.getTotal());
        responseResult.setData(page.getRecords());

        return responseResult;

    }

    @Autowired
    private WmNewsAutoScanService wmNewsAutoScanService; //自动审核

    @Autowired
    private WmNewsTaskService wmNewsTaskService;
    /**
     *  发布文章或保存草稿
     * @param dto
     * @return
     */
    @Override
    public ResponseResult submitNews(WmNewsDto dto) {
        //0.条件判断
        if(dto == null || dto.getContent() == null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //1.保存或修改文章

        WmNews wmNews = new WmNews();
        //属性拷贝 属性名词和类型相同才能拷贝
        BeanUtils.copyProperties(dto,wmNews);
        wmNews.setPublishTime(dto.getPublishTime());
        //封面图片  list---> string
        //dto中images传来的时list集合  news里面是字符串
        if(dto.getImages() != null && dto.getImages().size() > 0){
            //[1dddfsd.jpg,sdlfjldk.jpg]-->   1dddfsd.jpg,sdlfjldk.jpg
            String imageStr = StringUtils.join(dto.getImages(), ",");
            wmNews.setImages(imageStr);
        }
        //如果当前封面类型为自动 -1
        if(dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            wmNews.setType(null);
        }
        //保存或修改wmnews表
        saveOrUpdateWmNews(wmNews);
        //判断是否为保存草稿
        if (dto.getStatus().equals(WmNews.Status.NORMAL.getCode())){
            //是草稿 直接返回即可 无需保存关系表
            return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
        }

        //不是草稿,保存文本里面的图片与素材的关系
        //首先先将dto中传来的文本内容json数据提取除图片的List集合
        List<String> materias = ectractUrlInfo(dto.getContent());
        //将提取出来的图片与文本添加关系表
        saveRelativeInfo(materias,wmNews.getId(),WemediaConstants.WM_CONTENT_REFERENCE);


        //4.不是草稿，保存文章封面图片与素材的关系，如果当前布局是自动，需要匹配封面图片
        saveRelativeInfoForCover(dto,wmNews,materias);

        //审核文章
        //        wmNewsAutoScanService.autoScanWmNews(wmNews.getId());
        //添加到延迟队列中
        wmNewsTaskService.addNewsToTask(wmNews.getId(),wmNews.getPublishTime());
        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);

    }

    @Override
    public ResponseResult findById(Integer id) {
        WmNews wmNews = super.getById(id);
        return ResponseResult.okResult(wmNews);

    }

    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;
    //上架下架文章
    @Override
    public ResponseResult downOrUp(WmNewsDto dto) {
//        首先判断页面传的参数是否为空
        if (dto.getId()==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        WmNews wmNews = getById(dto.getId());
        if (wmNews==null){
            return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST,"文章不存在");
        }
//3.判断文章是否已发布
        if(!wmNews.getStatus().equals(WmNews.Status.PUBLISHED.getCode())){
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID,"当前文章不是发布状态，不能上下架");
        }
        //修改自媒体文章文章
        //4.修改文章enable
        if(dto.getEnable() != null && dto.getEnable() > -1 && dto.getEnable() < 2){
            update(Wrappers.<WmNews>lambdaUpdate().set(WmNews::getEnable,dto.getEnable())
                    .eq(WmNews::getId,wmNews.getId()));
        }
        //利用kafka发送消息修改article文章
        if (wmNews.getArticleId()!=null){
            Map map = new HashMap();
            map.put("articleId",wmNews.getArticleId());
            map.put("enable",dto.getEnable());
            kafkaTemplate.send(WmNewsMessageConstants.WM_NEWS_UP_OR_DOWN_TOPIC, JSON.toJSONString(map));
        }

        return ResponseResult.okResult(AppHttpCodeEnum.SUCCESS);
    }

    private void saveRelativeInfoForCover(WmNewsDto dto, WmNews wmNews, List<String> materias) {
        List<String> images = dto.getImages();

        //如果当前封面类型为自动，则设置封面类型的数据
        if(dto.getType().equals(WemediaConstants.WM_NEWS_TYPE_AUTO)){
            //多图
            if(materias.size() >= 3){
                wmNews.setType(WemediaConstants.WM_NEWS_MANY_IMAGE);
                images = materias.stream().limit(3).collect(Collectors.toList());
            }else if(materias.size() >= 1 && materias.size() < 3){
                //单图
                wmNews.setType(WemediaConstants.WM_NEWS_SINGLE_IMAGE);
                images = materias.stream().limit(1).collect(Collectors.toList());
            }else {
                //无图
                wmNews.setType(WemediaConstants.WM_NEWS_NONE_IMAGE);
            }

            //修改文章
            if(images != null && images.size() > 0){
                wmNews.setImages(StringUtils.join(images,","));
            }
            updateById(wmNews);
        }
        if(images != null && images.size() > 0){
            saveRelativeInfo(images,wmNews.getId(),WemediaConstants.WM_COVER_REFERENCE);
        }
    }

    @Autowired
    private WmMaterialMapper wmMaterialMapper;
    //添加文本与图片关系表
    private void saveRelativeInfo(List<String> materias, Integer id, Short wmContentReference) {
        if (materias!=null && materias.size()!=0){
            //先根据 materias这些图片路径 获取素材id
            List<WmMaterial> wmMaterials = wmMaterialMapper.selectList(Wrappers.<WmMaterial>lambdaQuery().in(WmMaterial::getUrl, materias));
            //判断素材是否有效
            if(wmMaterials==null || wmMaterials.size() == 0){
                //手动抛出异常   第一个功能：能够提示调用者素材失效了，第二个功能，进行数据的回滚
                throw new CustomException(AppHttpCodeEnum.PARAM_IMAGE_FORMAT_ERROR);
            }

            if(materias.size() != wmMaterials.size()){
                throw new CustomException(AppHttpCodeEnum.PARAM_IMAGE_FORMAT_ERROR);
            }

            List<Integer> idList = wmMaterials.stream().map(WmMaterial::getId).collect(Collectors.toList());

            //批量保存
            wmNewsMaterialMapper.saveRelations(idList,id,wmContentReference);
        }
    }

    //将传来的json字符串 解析出图片的url返回
    private List<String> ectractUrlInfo(String content) {
        List<String> materias = new ArrayList<>();
        List<Map> maps = JSON.parseArray(content, Map.class);
        for (Map map : maps) {
            if (map.get("type").equals("image")){
                String value = (String) map.get("value");
                materias.add(value);
            }
        }
        return materias;
    }

    @Autowired      //素材与文章关系表
    private WmNewsMaterialMapper wmNewsMaterialMapper;
    //保存或者修改wmnews表
    private void saveOrUpdateWmNews(WmNews wmNews) {
        //补全属性
        wmNews.setUserId(WmThreadLocalUtil.getUserId());
        wmNews.setCreatedTime(new Date());
      //  wmNews.setPublishTime(new Date());
        wmNews.setEnable((short) 1);//默认上架

        //判断前面传来的是否有id
        if (wmNews.getId()==null){
            //没有id代表要新增文章 草稿或发布
            save(wmNews);
        }else {
            //有id代表要修改文章
            //先删除与这个文章有关联的素材图片
            wmNewsMaterialMapper.delete(Wrappers.<WmNewsMaterial>lambdaQuery().eq(WmNewsMaterial::getNewsId,wmNews.getId()));
            updateById(wmNews);

        }
    }
}
