package cn.paperhub.paper.vo;

import java.util.List;
import java.time.LocalDateTime;
import java.math.BigDecimal;

public class PaperView {
    private Long id;
    private String title;
    private List<String> authors;
    @com.fasterxml.jackson.annotation.JsonProperty("abstract")
    private String abstractText;
    private String doi;
    private String journal;
    private Integer year;
    private List<String> keywords;
    private Long categoryId;
    private List<Long> tagIds;
    private String visibility;
    private Long uploaderId;
    private String uploaderName;
    private LocalDateTime uploadedAt;
    private Long downloads;
    private BigDecimal fileSizeMB;
    private Integer pageCount;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String reviewStatus;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String offlineReason;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String reviewComment;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
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
    public List<Long> getTagIds() { return tagIds; }
    public void setTagIds(List<Long> tagIds) { this.tagIds = tagIds; }
    public String getVisibility() { return visibility; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public Long getUploaderId() { return uploaderId; }
    public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }
    public String getUploaderName() { return uploaderName; }
    public void setUploaderName(String uploaderName) { this.uploaderName = uploaderName; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
    public Long getDownloads() { return downloads; }
    public void setDownloads(Long downloads) { this.downloads = downloads; }
    public BigDecimal getFileSizeMB() { return fileSizeMB; }
    public void setFileSizeMB(BigDecimal fileSizeMB) { this.fileSizeMB = fileSizeMB; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }
    public String getOfflineReason() { return offlineReason; }
    public void setOfflineReason(String offlineReason) { this.offlineReason = offlineReason; }
    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }
    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

}

