package cn.paperhub.reading.vo;

import cn.paperhub.reading.entity.NoteEntity;
import java.time.LocalDateTime;

public final class NoteView {
    public final Long id,paperId;
    public final String paperTitle,title,content;
    public final boolean paperAvailable;
    public final LocalDateTime createdAt,updatedAt;
    public NoteView(NoteEntity note,boolean available) {
        id=note.getId(); paperId=note.getPaperId(); paperTitle=note.getPaperTitle(); title=note.getTitle();
        content=note.getContent(); paperAvailable=available; createdAt=note.getCreatedAt(); updatedAt=note.getUpdatedAt();
    }
}

