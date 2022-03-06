package com.heima.minio.test;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class MinioTest {

    public static void main(String[] args) {


        try {
            //创建minio客户端用来上传
            MinioClient minioClient = MinioClient.builder()
                    //链接
                    .endpoint("http://192.168.200.130:9000/")
                    //账号密码
                    .credentials("minio", "minio123")
                    .build();
            FileInputStream fis = new FileInputStream("d://list.html");
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .object("aaa.html")//文件名字
                    .contentType("text/html")//文件类型
                    .bucket("leadnews")//桶名字  minio网页上创建的
                    .stream(fis, fis.available(), -1)
                    .build();
            minioClient.putObject(putObjectArgs);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ErrorResponseException e) {
            e.printStackTrace();
        } catch (InsufficientDataException e) {
            e.printStackTrace();
        } catch (InternalException e) {
            e.printStackTrace();
        } catch (InvalidBucketNameException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidResponseException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (ServerException e) {
            e.printStackTrace();
        } catch (XmlParserException e) {
            e.printStackTrace();
        }
    }
}
