package com.heima.wemedia.test;

import com.heima.common.aliyun.GreenImageScan;
import com.heima.common.aliyun.GreenTextScan;
import com.heima.file.service.FileStorageService;
import com.heima.wemedia.WemediaApplication;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Map;

@SpringBootTest(classes = WemediaApplication.class)
@RunWith(SpringRunner.class)
public class AliyunTest {

    @Autowired
    private GreenTextScan greenTextScan;

    @Autowired
    private GreenImageScan greenImageScan;

    @Autowired
    private FileStorageService fileStorageService;

    @Test
    public void testScanText() throws Exception {
        Map map = greenTextScan.greeTextScan("我是一个好人,冰毒");
        System.out.println(map);
    }

    @Test
    public void testScanImage() throws Exception {
        //byte[] bytes = fileStorageService.downLoadFile("http://192.168.200.130:9000/leadnews/2021/04/26/ef3cbe458db249f7bd6fb4339e593e55.jpg");
        byte[] bytes = new byte[1024];
        FileInputStream fis = new FileInputStream(new File("D://Capture001.png"));
        fis.read(bytes);
        Map map = greenImageScan.imageScan(Arrays.asList(bytes));
        System.out.println(map);
    }

    @Test
    public void testScanTexts() throws Exception {
        Map map = greenTextScan.greeTextScan("我是一个好人,冰毒");
        System.out.println(map);
    }
}