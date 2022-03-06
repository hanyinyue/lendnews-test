package com.heima.article.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.article.service.ApArticleService;
import com.heima.article.service.ArticleFreemarkerService;
import com.heima.file.service.FileStorageService;
import com.heima.model.article.pojos.ApArticle;
import com.heima.model.article.pojos.ApArticleContent;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;


@Service
@Transactional
public class ArticleFreemarkerServiceimpl implements ArticleFreemarkerService {
    /**
     * 生成静态文件上传到minIO中
     * @param apArticle
     * @param content
     */
    @Autowired
    private Configuration configuration;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private ApArticleService apArticleService;

    @Override
    @Async
    public void buildArticleToMinio(ApArticle apArticle, String content) {
            //判断context是否为空
        if (StringUtils.isNotBlank(content)){
            //存储生成的静态数据
            StringWriter writer =  new StringWriter();
            try {
                Template template = configuration.getTemplate("article.ftl");
                //添加数据模型
                Map<String,Object> contentDataModel = new HashMap<>();


                contentDataModel.put("context", JSONArray.parseArray(content));
                template.process(contentDataModel,writer);
            } catch (Exception e) {
                e.printStackTrace();
            }


            //上传到minio
            //4.3 把html文件上传到minio中
            InputStream in = new ByteArrayInputStream(writer.toString().getBytes());
            String path = fileStorageService.uploadHtmlFile("", apArticle.getId() + ".html", in);

            //修改aparticle里面的staticurl属性
            apArticleService.update(Wrappers.<ApArticle >lambdaUpdate()
                    .eq(ApArticle::getId,apArticle.getId())
                    .set(ApArticle::getStaticUrl,path));
        }
    }
}
