package cn.paperhub.paper.service;

import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.vo.PaperView;
import cn.paperhub.security.SessionPrincipal;
import java.util.List;

public final class PaperViewFactory {
    private PaperViewFactory() {}
    public static PaperView create(PaperEntity p, SessionPrincipal actor, String name, List<Long> tags) {
        PaperView v=new PaperView();
        v.setId(p.getId()); v.setTitle(p.getTitle()); v.setAuthors(p.getAuthors()); v.setAbstractText(p.getAbstractText());
        v.setDoi(p.getDoi()==null?"":p.getDoi()); v.setJournal(p.getJournal()==null?"":p.getJournal());
        v.setYear(p.getYear()); v.setKeywords(p.getKeywords()); v.setCategoryId(p.getCategoryId()); v.setTagIds(tags);
        v.setVisibility(p.getVisibility()); v.setUploaderId(p.getUploaderId()); v.setUploaderName(name);
        v.setUploadedAt(p.getUploadedAt()); v.setDownloads(p.getDownloads()); v.setFileSizeMB(p.getFileSizeMb());
        v.setPageCount(p.getPageCount()); v.setReviewStatus(p.getReviewStatus()); v.setOfflineReason(p.getOfflineReason());
        if(p.getUploaderId().equals(actor.getId()) || "admin".equals(actor.getRole())) v.setReviewComment(p.getReviewComment());
        v.setLink(p.getLink()); return v;
    }
}

