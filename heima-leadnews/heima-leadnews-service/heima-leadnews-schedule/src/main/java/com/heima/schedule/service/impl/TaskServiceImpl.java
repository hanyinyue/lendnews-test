package com.heima.schedule.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.heima.common.constants.ScheduleConstants;
import com.heima.common.redis.CacheService;
import com.heima.model.schedule.dtos.Task;
import com.heima.model.schedule.pojos.Taskinfo;
import com.heima.model.schedule.pojos.TaskinfoLogs;
import com.heima.schedule.mapper.TaskinfoLogsMapper;
import com.heima.schedule.mapper.TaskinfoMapper;
import com.heima.schedule.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
@Transactional
@Slf4j
public class TaskServiceImpl implements TaskService {

    @Autowired
    private TaskinfoMapper taskinfoMapper;
    @Autowired
    private TaskinfoLogsMapper  taskinfoLogsMapper;

    @Autowired
    private CacheService cacheService;

    @Scheduled(cron = "0 */1 * * * ?")
    //定时执行任务    (每分钟执行一次)   将zset的任务换到list中
    public void refresh(){

        String token = cacheService.tryLock("FUTURE_TASK_SYNC", 1000 * 30);
        if (StringUtils.isNotBlank(token)) {
            log.info(System.currentTimeMillis() / 1000 + "开始定时执行任务 将zset的任务转移到list中");
//        获取zset中存储的任务key
            //匹配future_ 的所有key值
            Set<String> futureKeys = cacheService.scan(ScheduleConstants.FUTURE + "*");

            for (String futureKey : futureKeys) {
                //将zset的key 转换成 list的key
                String topicKey = ScheduleConstants.TOPIC + futureKey.split(ScheduleConstants.FUTURE)[1];
                //获取当前需要消费的任务   从0到当前毫秒值
                Set<String> tasks = cacheService.zRangeByScore(futureKey, 0, System.currentTimeMillis());
                //如果任务不为空 删除zset里面的数据并且添加到list中
                if (!tasks.isEmpty()) {
                    cacheService.refreshWithPipeline(futureKey, topicKey, tasks);
                    System.out.println("成功的将" + futureKey + "下的当前需要执行的任务数据刷新到" + topicKey + "下");
                }

            }
        }
    }

    /**
     * 定时执行数据库的任务(5分钟内的)同步到zset中

     * @return
     */

    @Scheduled(cron = "0 */5 * * * ?")  //每五分钟执行一次
    @PostConstruct //此注解表示项目一启动先执行一次
    public void reloadData(){
        //首先先清理redis中为消费掉的任务 避免同步进去后造成任务重复
        clearCache();
        log.info("数据库数据同步到缓存");
        //查询数据库中五分钟内要执行的任务
        //1.1 获取当前时间五分钟后的时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE,5);
        long timeInMillis = calendar.getTimeInMillis();
        //查询执行时间小于5分钟后的任务清单
        List<Taskinfo> taskinfos = taskinfoMapper.selectList(Wrappers.<Taskinfo>lambdaQuery().lt(Taskinfo::getExecuteTime, timeInMillis));
        //添加任务到redis中
        if ( taskinfos!=null && taskinfos.size()!=0) {
            for (Taskinfo taskinfo : taskinfos) {
                Task task = new Task();
                BeanUtils.copyProperties(taskinfo,task);
                task.setExecuteTime(taskinfo.getExecuteTime().getTime());
                addTaskToCache(task);
            }
        }
    }

    //清理redis中的未消费的任务
    public  void clearCache(){
        //获取要消费的任务
        Set<String> topickeys = cacheService.scan(ScheduleConstants.TOPIC + "*");
        //获取五分钟内要消费的任务
        Set<String> futurekeys = cacheService.scan(ScheduleConstants.FUTURE + "*");
//        都清除掉
        cacheService.delete(topickeys);
        cacheService.delete(futurekeys);
    }

    //添加延迟任务
    @Override
    public long addTask(Task task) {
        //先将task保存到数据库中  (taskinfo表和taskinfologs表)
        boolean flag = addTaskToDb(task);
        if (flag){
//            存入redis
            addTaskToCache(task);
        }
        return task.getTaskId();

    }

    //取消任务   删除任务表taskinfo  修改taskinfologs  删除redis中的任务
    @Override
    public boolean cancelTask(long taskId) {
        //删除任务表taskinfo  修改taskinfologs
        //因为一会要清除redis的数据 用到key所以返回task
        Task task = updateDb(taskId,ScheduleConstants.CANCELLED );
        boolean flag = false;
        if (task!=null){
            //说明修改数据库完成 清除redis中的数据
            removeTaskFromCache(task);
            flag = true;
        }

        return flag;
    }
    /**
     * 按照类型和优先级来拉取任务
     * @param type 类型
     * @param priority  优先级
     * @return
     */
    //拉取任务 修改数据库 从redis的list里面获取并删除任务
    public Task poll(int type,int priority){
        Task task = null;
        String key = type+"_"+priority;
        try {
            String taskValue = cacheService.lRightPop(ScheduleConstants.TOPIC + key);
            if (StringUtils.isNotBlank(taskValue)) {
                 task = JSON.parseObject(taskValue, Task.class);
                updateDb(task.getTaskId(),ScheduleConstants.EXECUTED);
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.error("poll task exception[拉取延迟任务失败]");
        }

        return task;
    }

    //清除redis中的数据
    private void removeTaskFromCache(Task task) {
        String key = task.getTaskType()+"_"+task.getPriority();

        if(task.getExecuteTime()<=System.currentTimeMillis()){
            cacheService.lRemove(ScheduleConstants.TOPIC+key,0,JSON.toJSONString(task));
        }else {
            cacheService.zRemove(ScheduleConstants.FUTURE+key, JSON.toJSONString(task));
        }
    }

    private Task updateDb(long taskId, int status) {
        Task task = null;

        try {
            taskinfoMapper.deleteById(taskId);

            TaskinfoLogs taskinfoLogs = taskinfoLogsMapper.selectById(taskId);
            if (taskinfoLogs!=null){
                taskinfoLogs.setStatus(status);
                taskinfoLogsMapper.updateById(taskinfoLogs);

                task =new Task();
                BeanUtils.copyProperties(taskinfoLogs,task);
                task.setExecuteTime(taskinfoLogs.getExecuteTime().getTime());
            }
        } catch (BeansException e) {
            log.error("task cancel exception taskid={},取消任务失败",taskId);
            e.printStackTrace();
        }
            return task;

    }

    //添加任务到redis
    private void addTaskToCache(Task task) {
        String key = task.getTaskType() + "_" + task.getPriority();
        //获取当前时间的五分钟后的时间
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE,5);
        long timeInMillis = calendar.getTimeInMillis();
        long l = System.currentTimeMillis();
        //添加到redis中;利用工具类cacheService
        if (task.getExecuteTime()<=System.currentTimeMillis()){
            //如果要执行的时间小于等于当前时间,就添加到redis中list集合里面,立刻消费
            cacheService.lLeftPush(ScheduleConstants.TOPIC + key, JSON.toJSONString(task));
        }else if (task.getExecuteTime()<= timeInMillis){
            //如果要执行的任务时间在当前时间的5分钟之内,就添加到zset中  (key,value,score可排序分值)
            cacheService.zAdd(ScheduleConstants.FUTURE + key, JSON.toJSONString(task), task.getExecuteTime());
        }
    }

    //添加任务到数据库
    private boolean addTaskToDb(Task task) {
        //添加到数据库
        //添加到taskinfo
        boolean flag = false;

        try {
            //保存任务表
            Taskinfo taskinfo = new Taskinfo();
            BeanUtils.copyProperties(task, taskinfo);
            taskinfo.setExecuteTime(new Date(task.getExecuteTime()));
            taskinfoMapper.insert(taskinfo);

            //设置taskID
            task.setTaskId(taskinfo.getTaskId());

            //保存任务日志数据
            TaskinfoLogs taskinfoLogs = new TaskinfoLogs();
            BeanUtils.copyProperties(taskinfo, taskinfoLogs);

            taskinfoLogs.setVersion(1);
            taskinfoLogs.setStatus(ScheduleConstants.SCHEDULED);
            taskinfoLogsMapper.insert(taskinfoLogs);

            flag = true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return flag;
    }
}
