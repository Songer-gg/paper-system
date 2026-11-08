package cn.paperhub.admin.vo;

import java.time.LocalDateTime;

public final class OperationLogView {
    public Long id;
    public String userName,action,target,result;
    public LocalDateTime time;
}
