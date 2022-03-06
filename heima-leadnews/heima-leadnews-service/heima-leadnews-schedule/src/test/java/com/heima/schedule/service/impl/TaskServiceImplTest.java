package com.heima.schedule.service.impl;

import com.heima.model.schedule.dtos.Task;
import com.heima.schedule.ScheduleApplication;
import com.heima.schedule.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ScheduleApplication.class)
@RunWith(SpringRunner.class)
class TaskServiceImplTest {

    @Autowired
    private TaskService taskService;
    @Test
    void addTask() {
        Task task = new Task();
        task.setExecuteTime(new Date().getTime()+10000);
        task.setParameters("task test11".getBytes());
        task.setTaskType(100);
        task.setPriority(50);
        long taskId = taskService.addTask(task);
        System.out.println(taskId);
    }

    @Test
    void cancelTask() {
        taskService.cancelTask(1483729388226232321L);
    }

    @Test
    void poll() {
        Task task = taskService.poll(100, 50);
        System.out.println(task);
    }
}