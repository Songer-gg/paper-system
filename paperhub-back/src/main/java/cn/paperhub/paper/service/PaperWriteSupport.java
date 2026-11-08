package cn.paperhub.paper.service;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.paper.entity.PaperEntity;
import cn.paperhub.paper.mapper.PaperMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import java.io.*;
import java.security.*;
import java.util.*;
import cn.paperhub.common.storage.LocalFileStorage;
import org.springframework.stereotype.Component;

@Component
public class PaperWriteSupport {
    private final PaperMapper mapper;
    private final PaperValidation validation;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final LocalFileStorage storage;
    public PaperWriteSupport(PaperMapper mapper,PaperValidation validation,
            org.springframework.jdbc.core.JdbcTemplate jdbc,LocalFileStorage storage) {
        this.mapper=mapper; this.validation=validation; this.jdbc=jdbc; this.storage=storage;
    }
    public PaperEntity active(long id) {
        PaperValidation.id(id); PaperEntity paper=mapper.lockById(id);
        if(paper==null || paper.getDeletedAt()!=null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return paper;
    }
    public void save(PaperEntity paper) {
        long version=paper.getVersion(); paper.setVersion(version+1);
        if(mapper.update(paper,new UpdateWrapper<PaperEntity>().eq("id",paper.getId()).eq("version",version)
                .isNull("deleted_at"))!=1) throw new BusinessException(ErrorCode.CONFLICT);
    }
    public void publication(PaperEntity paper) {
        if(!Boolean.TRUE.equals(paper.getCopyrightAgreed())) reject("缺少版权确认");
        java.util.List<Long> tags=mapper.tagIds(paper.getId());
        validation.validate(paper,tags);
        if(paper.getCategoryId()!=null && !platform("category",paper.getCategoryId())) reject("共享论文须使用平台分类");
        for(Long tag:tags) if(!platform("tag",tag)) reject("共享论文须使用平台标签");
        paper.setDoiNormalized(validation.normalizeDoi(paper.getDoi()));
        if(paper.getFileSha256()==null || paper.getPageCount()==null || paper.getPageCount()<1)
            reject("原始 PDF 不可用，请重新上传");
        try(InputStream input=storage.open(paper.getFilePath())) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            byte[] buffer=new byte[8192]; int count; long size=0;
            while((count=input.read(buffer))!=-1) {
                size+=count;
                if(size>LocalFileStorage.PDF_LIMIT) reject("原始 PDF 大小不合法");
                digest.update(buffer,0,count);
            }
            StringBuilder hash=new StringBuilder();
            for(byte b:digest.digest()) hash.append(String.format(Locale.ROOT,"%02x",b&255));
            if(size==0 || !hash.toString().equals(paper.getFileSha256())) reject("原始 PDF 已变化，请重新上传");
        } catch(IOException ex) { reject("原始 PDF 不可用，请重新上传"); }
        catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private boolean platform(String table,long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE id=? AND owner_id IS NULL",Long.class,id)==1;
    }
    private void reject(String message) { throw new BusinessException(ErrorCode.BIZ_REJECTED,message); }
}

