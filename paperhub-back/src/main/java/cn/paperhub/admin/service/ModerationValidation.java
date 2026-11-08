package cn.paperhub.admin.service;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class ModerationValidation {
    public String result(String value) {
        String clean=text(value,500);
        if(clean.isEmpty()) throw new BusinessException(ErrorCode.BIZ_REJECTED,"请填写举报处理结果");
        return clean;
    }
    public String action(String value) {
        String clean=text(value,50);
        if(clean.isEmpty()) invalid();return clean;
    }
    private String text(String value,int max) {
        if(value==null || !StandardCharsets.UTF_8.newEncoder().canEncode(value)) invalid();
        int first=0,last=value.length();
        while(first<last && space(value.codePointAt(first))) first+=Character.charCount(value.codePointAt(first));
        while(last>first && space(value.codePointBefore(last))) last-=Character.charCount(value.codePointBefore(last));
        String clean=value.substring(first,last);
        if(clean.codePointCount(0,clean.length())>max || clean.indexOf(0)>=0) invalid();return clean;
    }
    private boolean space(int c) { return Character.isWhitespace(c)||Character.isSpaceChar(c)||c==0xFEFF; }
    private void invalid() { throw new BusinessException(ErrorCode.INVALID_PARAM,"处理说明或日志类型格式不合法"); }
}
