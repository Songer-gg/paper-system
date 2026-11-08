package cn.paperhub.reading.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import cn.paperhub.paper.service.PaperValidation;

public class FavoriteRequest {
    private Boolean favorited;
    public Boolean getFavorited() { return favorited; }
    @JsonSetter("favorited")
    public void setFavorited(Boolean value) {
        if(value==null) PaperValidation.invalid();
        favorited=value;
    }
}

