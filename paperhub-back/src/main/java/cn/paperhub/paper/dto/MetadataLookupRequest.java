package cn.paperhub.paper.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class MetadataLookupRequest {
    @NotBlank @Size(max=300) private String doi;
    public String getDoi() { return doi; }
    public void setDoi(String value) { doi=value; }
}
