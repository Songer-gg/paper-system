package cn.paperhub.reading.vo;

public final class FavoriteState {
    public final long paperId;
    public final boolean favorited;
    public FavoriteState(long paperId,boolean favorited) { this.paperId=paperId; this.favorited=favorited; }
}

