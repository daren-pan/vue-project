package com.ruoyi.system.controller.workflow;

import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.system.domain.workflow.WfProcessDefinition;
import com.ruoyi.system.service.workflow.IWfProcessDefinitionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * 流程定义管理 Controller
 * 
 * 对应页面：系统管理 → 工作流 → 流程定义
 * 功能：管理所有的流程模板（新增、编辑、查询、删除）
 * 例如：请假审批、报销审批、合同审批等流程的模板配置
 */
@RestController
@RequestMapping("/workflow/definition")
public class WfProcessDefinitionController extends BaseController {

    @Autowired
    private IWfProcessDefinitionService definitionService;

    /**
     * 分页查询流程定义列表
     * 支持按流程标识、流程名称、状态进行模糊查询
     */
    @GetMapping("/list")
    public TableDataInfo list(WfProcessDefinition definition) {
        startPage();
        List<WfProcessDefinition> list = definitionService.selectWfProcessDefinitionList(definition);
        return getDataTable(list);
    }

    /**
     * 根据ID查询流程定义详情
     * 用于编辑页面回显数据
     */
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(definitionService.selectWfProcessDefinitionById(id));
    }

    /**
     * 新增流程定义
     * 创建新的流程模板（如新增"加班审批"流程）
     */
    @PostMapping
    @Log(title = "流程定义", businessType = BusinessType.INSERT)
    public AjaxResult add(@RequestBody WfProcessDefinition definition) {
        return toAjax(definitionService.insertWfProcessDefinition(definition));
    }

    /**
     * 修改流程定义
     * 编辑流程名称、启用/停用等基本信息
     * 注意：修改版本号后，新发起的流程使用新版本，已有流程不受影响
     */
    @PutMapping
    @Log(title = "流程定义", businessType = BusinessType.UPDATE)
    public AjaxResult edit(@RequestBody WfProcessDefinition definition) {
        return toAjax(definitionService.updateWfProcessDefinition(definition));
    }

    /**
     * 删除流程定义（逻辑删除）
     * 删除后已有进行中的流程不受影响，但不能再基于该流程发起新流程
     */
    @DeleteMapping("/{id}")
    @Log(title = "流程定义", businessType = BusinessType.DELETE)
    public AjaxResult remove(@PathVariable Long id) {
        return toAjax(definitionService.deleteWfProcessDefinitionById(id));
    }
}
