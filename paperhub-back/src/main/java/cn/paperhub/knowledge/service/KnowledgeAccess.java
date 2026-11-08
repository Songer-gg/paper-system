package cn.paperhub.knowledge.service;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.security.SessionPrincipal;
import java.util.Objects;

final class KnowledgeAccess {
    private KnowledgeAccess() {}
    static boolean admin(SessionPrincipal actor) { return "admin".equals(actor.getRole()); }
    static void readable(SessionPrincipal actor, Long ownerId) {
        if(ownerId!=null && !Objects.equals(ownerId,actor.getId()) && !admin(actor))
            throw new BusinessException(ErrorCode.NOT_FOUND);
    }
    static void writable(SessionPrincipal actor, Long ownerId) {
        readable(actor,ownerId);
        if(ownerId==null && !admin(actor)) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
    static String name(String name) {
        if(name==null) throw new BusinessException(ErrorCode.INVALID_PARAM);
        String clean=name.trim();
        if(clean.length()>50 || clean.isEmpty() ||
                clean.codePoints().allMatch(c->Character.isWhitespace(c)||Character.isSpaceChar(c)))
            throw new BusinessException(ErrorCode.INVALID_PARAM,"名称须为1–50字符");
        return clean;
    }
}

