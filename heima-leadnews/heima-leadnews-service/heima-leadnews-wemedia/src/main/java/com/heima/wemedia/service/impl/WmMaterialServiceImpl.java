package com.heima.wemedia.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.file.service.FileStorageService;
import com.heima.model.common.dtos.PageResponseResult;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.wemedia.dtos.WmMaterialDto;
import com.heima.model.wemedia.pojos.WmMaterial;
import com.heima.utils.thread.WmThreadLocalUtil;
import com.heima.wemedia.mapper.WmMaterialMapper;
import com.heima.wemedia.service.WmMaterialService;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.shaded.protobuf.generated.HBaseProtos;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

@Service
@Transactional
@Slf4j
public class WmMaterialServiceImpl extends ServiceImpl<WmMaterialMapper, WmMaterial> implements WmMaterialService {


    @Autowired  //注入自己写的自动上传minio的类
    private FileStorageService fileStorageService;
    /**
     * 上传素材图片
     * @param multipartFile
     * @return
     */
    @Override
    public ResponseResult upload_picture(MultipartFile multipartFile) {
        //检查传过来的参数
        if (multipartFile==null || multipartFile.getSize()==0){
            //传过来的为空
            return ResponseResult.errorResult(AppHttpCodeEnum.PARAM_INVALID);
        }
        //        生成图片名字
        String filename = multipartFile.getOriginalFilename();
        String substring = filename.substring(filename.lastIndexOf("."));
        filename = UUID.randomUUID().toString().replace("-","")+substring;
        //上传到minio中
        String imgurl = null;
        try {
            imgurl = fileStorageService.uploadImgFile("", filename, multipartFile.getInputStream());
            log.info("上传图片到MinIO中，imgurl:{}",imgurl);
        } catch (IOException e) {
            log.error("WmMaterialServiceImpl-上传图片失败");
            e.printStackTrace();
        }
        //保存到数据库
        WmMaterial wmMaterial = new WmMaterial();
        wmMaterial.setUserId(WmThreadLocalUtil.getUserId());
        wmMaterial.setCreatedTime(new Date());
        wmMaterial.setType((short) 0);
        wmMaterial.setUrl(imgurl);
        wmMaterial.setIsCollection((short) 0);
        super.save(wmMaterial);

        return ResponseResult.okResult(wmMaterial);
    }

    /**
     * 查看素材列表
     * @param dto
     * @return
     */
    @Override
    public ResponseResult listPicture(WmMaterialDto dto) {
        //检测参数
        dto.checkParam();

        IPage<WmMaterial> page = new Page<>(dto.getPage(),dto.getSize());
        Integer userId = WmThreadLocalUtil.getUserId();
        LambdaQueryWrapper<WmMaterial> lqw = new LambdaQueryWrapper<>();
        lqw.eq(WmMaterial::getUserId,userId);
        lqw.orderByDesc(WmMaterial::getCreatedTime);
        lqw.eq(dto.getIsCollection()!=null,WmMaterial::getIsCollection,dto.getIsCollection());
        page = page(page, lqw);
        ResponseResult responseResult =new PageResponseResult((int)page.getPages(),(int)page.getSize(),(int)page.getTotal());
        responseResult.setData(page.getRecords());
        return responseResult;
    }
}
