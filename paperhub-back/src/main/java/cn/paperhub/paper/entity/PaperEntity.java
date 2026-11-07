package cn.paperhub.paper.entity;

import java.util.List;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.*;
import cn.paperhub.paper.mapper.StringListTypeHandler;
@TableName(value = "paper", autoResultMap = true)
public class PaperEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> authors;
    @TableField("abstract")
    private String abstractText;
    private String doi;
    private String journal;
    private Integer year;
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> keywords;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long categoryId;
    private String visibility;
    private Long uploaderId;
    private LocalDateTime uploadedAt;
    private Long downloads;
    private BigDecimal fileSizeMb;
    private Integer pageCount;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reviewStatus;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String offlineReason;
    private LocalDateTime deletedAt;
    private Long version;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reviewComment;
    private String filePath;
    private String fileSha256;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String doiNormalized;
    private Boolean copyrightAgreed;
    private LocalDateTime copyrightAgreedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String link;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public List<String> getAuthors() { return authors; }
    public void setAuthors(List<String> authors) { this.authors = authors; }
    public String getAbstractText() { return abstractText; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; }
    public String getDoi() { return doi; }
    public void setDoi(String doi) { this.doi = doi; }
    public String getJournal() { return journal; }
    public void setJournal(String journal) { this.journal = journal; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Long getUploaderId() { return uploaderId; }
    public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public Long getDownloads() { return downloads; }
    public void setDownloads(Long downloads) { this.downloads = downloads; }
    public BigDecimal getFileSizeMb() { return fileSizeMb; }
    public void setFileSizeMb(BigDecimal fileSizeMb) { this.fileSizeMb = fileSizeMb; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getOfflineReason() { return offlineReason; }
    public void setOfflineReason(String offlineReason) { this.offlineReason = offlineReason; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFileSha256() { return fileSha256; }
    public void setFileSha256(String fileSha256) { this.fileSha256 = fileSha256; }
    public String getDoiNormalized() { return doiNormalized; }
    public void setDoiNormalized(String doiNormalized) { this.doiNormalized = doiNormalized; }
    public Boolean getCopyrightAgreed() { return copyrightAgreed; }
    public void setCopyrightAgreed(Boolean copyrightAgreed) { this.copyrightAgreed = copyrightAgreed; }
    public LocalDateTime getCopyrightAgreedAt() { return copyrightAgreedAt; }
    public void setCopyrightAgreedAt(LocalDateTime copyrightAgreedAt) { this.copyrightAgreedAt = copyrightAgreedAt; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

}

