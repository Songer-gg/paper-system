package cn.paperhub.admin.vo;

import java.time.LocalDateTime;

public final class AdminUserView {
    public Long id;
    public String username,name,email,role,status;
    public LocalDateTime registeredAt,passwordResetAt;
    public long sharedCount,contributionCount;
}
