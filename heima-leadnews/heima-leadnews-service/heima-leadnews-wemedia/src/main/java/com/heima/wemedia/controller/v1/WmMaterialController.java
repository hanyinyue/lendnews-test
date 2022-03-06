package com.heima.wemedia.controller.v1;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.WmMaterialDto;
import com.heima.wemedia.service.WmMaterialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/material")
public class WmMaterialController {

    @Autowired
    private WmMaterialService wmMaterialService;
    /**
     * 上传素材图片
     * @param multipartFile
     * @return
     */
    @PostMapping("upload_picture")
    public ResponseResult upload_picture(MultipartFile multipartFile){

    return wmMaterialService.upload_picture(multipartFile);
    }

    /**
     * 查看素材列表
     * @param wmMaterialDto
     * @return
     */
    @PostMapping("/list")
    public ResponseResult list(@RequestBody WmMaterialDto wmMaterialDto){

        return wmMaterialService.listPicture(wmMaterialDto);
    }
}