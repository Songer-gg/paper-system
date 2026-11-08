package cn.paperhub.paper.dto;

import javax.validation.constraints.*;

public class SharedQuery {
    @Size(max=255) private String q;
    private Long categoryId;
    @Size(max=900) private String tagIds;
    @Min(1000) @Max(9999) private Integer year;
    @Size(max=255) private String journal;
    @Pattern(regexp="uploaded-desc|downloads-desc|year-desc") private String sort;
    @NotNull @Min(1) private Integer page=1;
    @NotNull @Min(1) @Max(100) private Integer pageSize=10;
    public String getQ() { return q; }
    public void setQ(String value) { q=value; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long value) { categoryId=value; }
    public String getTagIds() { return tagIds; }
    public void setTagIds(String value) { tagIds=value; }
    public Integer getYear() { return year; }
    public void setYear(Integer value) { year=value; }
    public String getJournal() { return journal; }
    public void setJournal(String value) { journal=value; }
    public String getSort() { return sort; }
    public void setSort(String value) { sort=value; }
    public Integer getPage() { return page; }
    public void setPage(Integer value) { page=value; }
    public Integer getPageSize() { return pageSize; }
    public void setPageSize(Integer value) { pageSize=value; }
}

