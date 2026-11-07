package cn.paperhub.profile.vo;

import java.time.LocalDateTime;

public final class MyProfileView {
    public final Long id;
    public final String username, name, email, role, status, avatar;
    public final LocalDateTime registeredAt;
    public final long uploadedCount;
    public MyProfileView(cn.paperhub.auth.entity.UserAccount user, String avatar, long count) {
        id=user.getId(); username=user.getUsername(); name=user.getName(); email=user.getEmail();
        role=user.getRole(); status=user.getStatus(); registeredAt=user.getRegisteredAt();
        this.avatar=avatar; uploadedCount=count;
    }
}

