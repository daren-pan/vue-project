package com.ruoyi.common.core.utils;

import com.alibaba.fastjson2.JSON;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 对象字段比对工具
 *
 * @author ruoyi
 */
public class CompareUtil {

    /**
     * 对比两个对象的同名字段，返回变更摘要
     *
     * @param oldObj 旧对象
     * @param newObj 新对象
     * @return {"deptName":"深圳总公司→广州总公司","status":"0→1"}
     */
    public static String diff(Object oldObj, Object newObj) {
        if (oldObj == null && newObj == null) return "{}";
        if (oldObj == null) return "{}";  // 新增，无旧值
        if (newObj == null) return "{}";  // 删除，无新值

        Map<String, String> changes = new LinkedHashMap<>();
        Field[] fields = newObj.getClass().getDeclaredFields();

        for (Field field : fields) {
            if ("serialVersionUID".equals(field.getName())) continue;
            try {
                field.setAccessible(true);
                Object oldVal = field.get(oldObj);
                Object newVal = field.get(newObj);
                if (oldVal == null && newVal == null) continue;
                if (oldVal != null && oldVal.equals(newVal)) continue;

                String oldStr = oldVal != null ? subStr(String.valueOf(oldVal), 100) : "null";
                String newStr = newVal != null ? subStr(String.valueOf(newVal), 100) : "null";
                changes.put(field.getName(), oldStr + "→" + newStr);
            } catch (Exception ignored) {}
        }
        return JSON.toJSONString(changes);
    }

    private static String subStr(String str, int maxLen) {
        return str.length() <= maxLen ? str : str.substring(0, maxLen) + "...";
    }
}
