package com.heima.wemedia.controller.v1;

import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.wemedia.dtos.WmNewsDto;
import com.heima.model.wemedia.dtos.WmNewsPageReqDto;
import com.heima.wemedia.service.WmNewsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/news")
public class WmNewsController {

    @Autowired
    private WmNewsService wmNewsService;

    /**
     * 查询所有自媒体文章 (文章列表)
     * @param dto
     * @return
     */
    @PostMapping("/list")
    public ResponseResult findAll(@RequestBody WmNewsPageReqDto dto){
        return  wmNewsService.findAll(dto);
    }

    /**
     * 发布或修改文章
     * @param dto
     * @return
     */
    @PostMapping("/submit")
    public ResponseResult saveOrUpdate(@RequestBody WmNewsDto dto){
        return wmNewsService.submitNews(dto);

    }

    /**
     * 上架或下架文章
     * @param dto
     * @return
     */
    @PostMapping("/down_or_up")
    public ResponseResult downOrUp(@RequestBody WmNewsDto dto){
        return wmNewsService.downOrUp(dto);
    }

//    @GetMapping("/one/#{id}")
//    public ResponseResult findById(@PathVariable Integer id ){
//        return wmNewsService.findById(id);
//    }

}