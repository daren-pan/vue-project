package com.ruoyi.system.domain.AsyncTask;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TaskStore {
    // key: taskId, value: 任务状态对象
    private final Map<String, TaskStatus> store = new ConcurrentHashMap<>();

    public void put(String taskId, TaskStatus status) {
        store.put(taskId, status);
    }

    public TaskStatus get(String taskId) {
        return store.get(taskId);
    }

    public void updateStatus(String taskId, String status, Object result) {
        TaskStatus ts = store.get(taskId);
        if (ts != null) {
            ts.setStatus(status);
            ts.setResult(result);
        }
    }

    public void remove(String taskId) {
        store.remove(taskId);
    }

    // 任务状态对象
    public static class TaskStatus {
        private String status;  // PROCESSING, SUCCESS, FAILED
        private Object result;  // 成功时存放结果，失败时存放错误信息
        // getter/setter...

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Object getResult() {
            return result;
        }

        public void setResult(Object result) {
            this.result = result;
        }
    }
}
