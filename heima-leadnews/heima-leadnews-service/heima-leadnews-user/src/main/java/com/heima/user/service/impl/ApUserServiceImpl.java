package com.heima.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.heima.model.common.dtos.ResponseResult;
import com.heima.model.common.enums.AppHttpCodeEnum;
import com.heima.model.user.dtos.LoginDto;
import com.heima.model.user.pojos.ApUser;
import com.heima.user.mapper.ApUserMapper;
import com.heima.user.service.ApUserService;
import com.heima.utils.common.AppJwtUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.sql.Wrapper;
import java.util.HashMap;
import java.util.Map;

@Service
public class ApUserServiceImpl extends ServiceImpl<ApUserMapper, ApUser> implements ApUserService {


    //用户登录校验
    @Override
    public ResponseResult login(LoginDto dto) {
        //判断手机号或者密码是否为空
        if (!StringUtils.isBlank(dto.getPhone()) && !StringUtils.isBlank(dto.getPassword())){
            //不为空的话 根据手机号查询是否注册过
            ApUser apUser = this.getOne(Wrappers.lambdaQuery(ApUser.class).eq(ApUser::getPhone, dto.getPhone()));
            if (apUser==null){
                //手机号不存在
                return ResponseResult.errorResult(AppHttpCodeEnum.DATA_NOT_EXIST,"用户不存在");
            }
            //手机号存在 校验密码 获取随机的salt
            String password = dto.getPassword()+apUser.getSalt();
            //生成用户输入的密码加上saltmd5加密的
            password = DigestUtils.md5DigestAsHex(password.getBytes());
            if (!password.equals(apUser.getPassword())){
                //密码错误
                return ResponseResult.errorResult(AppHttpCodeEnum.LOGIN_PASSWORD_ERROR);
            }
            //密码正确 生成token
            String token = AppJwtUtil.getToken(apUser.getId().longValue());
            Map map = new HashMap();
            map.put("token",token);
            apUser.setSalt("");
            apUser.setPassword("");
            map.put("user", apUser);
            return ResponseResult.okResult(map);
        }else {
            //用户输入为空,游客模式  返回token
            Map map = new HashMap();
            map.put("token", AppJwtUtil.getToken(0l));
            return ResponseResult.okResult(map);
        }

    }
}
