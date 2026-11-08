package cn.paperhub.profile.service;

import cn.paperhub.audit.service.AuditService;
import cn.paperhub.auth.mapper.UserAccountMapper;
import cn.paperhub.auth.entity.UserAccount;
import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.common.storage.*;
import cn.paperhub.profile.vo.MyProfileView;
import cn.paperhub.security.SessionPrincipal;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AvatarService {
    private final LocalFileStorage storage;
    private final FileLifecycle files;
    private final UserAccountMapper users;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public AvatarService(LocalFileStorage storage, FileLifecycle files, UserAccountMapper users, JdbcTemplate jdbc, AuditService audit) {
        this.storage=storage; this.files=files; this.users=users; this.jdbc=jdbc; this.audit=audit;
    }
    public String display(String key) {
        if(key==null || !key.matches("avatars/[a-fA-F0-9-]{36}\\.png")) return "";
        try(InputStream in=storage.open(key); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int count;
            while((count=in.read(buffer))!=-1) {
                if(out.size()+count>LocalFileStorage.AVATAR_LIMIT) return "";
                out.write(buffer,0,count);
            }
            return "data:image/png;base64,"+Base64.getEncoder().encodeToString(normalize(out.toByteArray()));
        } catch(IOException ex) { return ""; }
    }
    @Transactional(timeout=60,rollbackFor=Exception.class)
    public MyProfileView update(MultipartFile file, SessionPrincipal actor) throws IOException {
        if(file.isEmpty() || file.getSize()>LocalFileStorage.AVATAR_LIMIT) invalid();
        byte[] source;
        try(InputStream in=file.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192]; int count;
            while((count=in.read(buffer))!=-1) {
                if(out.size()+count>LocalFileStorage.AVATAR_LIMIT) invalid();
                out.write(buffer,0,count);
            }
            source=out.toByteArray();
        }
        byte[] png=normalize(source);
        jdbc.queryForObject("SELECT id FROM `user` WHERE id=? FOR UPDATE",Long.class,actor.getId());
        UserAccount user=users.selectById(actor.getId());
        LocalFileStorage.Stored stored=storage.avatar(png); files.created(stored);
        files.deleting(user.getAvatar());
        jdbc.update("UPDATE `user` SET avatar=? WHERE id=?",stored.key,actor.getId());
        audit.mutation(actor,"头像修改","ownerId="+actor.getId());
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM paper WHERE uploader_id=? AND deleted_at IS NULL",Long.class,actor.getId());
        // Preserve the frontend's existing image-src contract, without storing a dataURL in VARCHAR(255).
        return new MyProfileView(user,"data:image/png;base64,"+Base64.getEncoder().encodeToString(png),count);
    }
    private byte[] normalize(byte[] source) throws IOException {
        try(ImageInputStream input=new javax.imageio.stream.MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext()) invalid();
            ImageReader reader=readers.next();
            try {
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!format.equals("png") && !format.equals("jpeg") && !format.equals("jpg")) invalid();
                reader.setInput(input,true,true);
                int width=reader.getWidth(0), height=reader.getHeight(0);
                if(width<1 || height<1 || width>2048 || height>2048 || (long)width*height>4_194_304) invalid();
                BufferedImage image=reader.read(0);
                try(ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                    if(!ImageIO.write(image,"png",out) || out.size()>LocalFileStorage.AVATAR_LIMIT) invalid();
                    return out.toByteArray();
                }
            } finally { reader.dispose(); }
        } catch(IOException | IndexOutOfBoundsException ex) { invalid(); return null; }
    }
    private void invalid() { throw new BusinessException(ErrorCode.INVALID_PARAM,"头像须为有效 PNG/JPEG，最大 2 MiB、2048×2048"); }
}

