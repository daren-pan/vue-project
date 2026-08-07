package com.ruoyi.common.core.exception;

/**
 * 工作流异常
 *
 * @author ruoyi
 */
public class WorkflowException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private Integer code;

    /**
     * 错误提示
     */
    private String message;

    public WorkflowException()
    {
    }

    public WorkflowException(String message)
    {
        this.message = message;
    }

    public WorkflowException(String message, Integer code)
    {
        this.message = message;
        this.code = code;
    }

    @Override
    public String getMessage()
    {
        return message;
    }

    public Integer getCode()
    {
        return code;
    }
}
