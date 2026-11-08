package cn.paperhub.admin.vo;

import cn.paperhub.common.storage.LocalFileStorage;

public final class PlatformSettingsView {
    public final String platformName="PaperHub 论文管理平台";
    public final String slogan="面向科研人员的学术论文管理与共享平台";
    public final String logoText="PH";
    public final String version="V1.0";
    public final long maxUploadMB=LocalFileStorage.PDF_LIMIT/(1024*1024);
    public final String allowedTypes=".pdf";
    public final String contactEmail="support@paperhub.example.cn";
}
