package cn.paperhub.paper.vo;

import com.fasterxml.jackson.annotation.JsonInclude;

public final class DuplicateCheckView {
    public final String level,message;
    @JsonInclude(JsonInclude.Include.NON_NULL) public final PaperView paper;
    public DuplicateCheckView(String level,String message,PaperView paper) {
        this.level=level; this.message=message; this.paper=paper;
    }
}

