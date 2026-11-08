package cn.paperhub.profile.service;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class AccountValidation {
    public String name(String value) {
        String clean=trim(value);
        if(clean.isEmpty() || clean.codePointCount(0,clean.length())>50 || !unicode(clean)) invalid();
        return clean;
    }
    public String email(String value) {
        String clean=trim(value).toLowerCase(Locale.ROOT);
        if(clean.length()>120 || !clean.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]*[a-z0-9])?)+")) invalid();
        String local=clean.substring(0,clean.indexOf('@'));
        if(local.startsWith(".") || local.endsWith(".") || local.contains("..")) invalid();
        return clean;
    }
    public void password(String value,boolean fresh) {
        if(value==null || !unicode(value)) invalid();
        int bytes=value.getBytes(StandardCharsets.UTF_8).length;
        if(bytes>(72) || bytes<(fresh?8:1) || trim(value).isEmpty() || value.indexOf(0)>=0) invalid();
    }
    public String keyword(String value) {
        if(value==null) return null;
        String clean=trim(value);
        if(clean.codePointCount(0,clean.length())>120 || !unicode(clean)) invalid();
        if(clean.isEmpty()) return null;
        return "%"+clean.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    }
    private boolean unicode(String value) { return StandardCharsets.UTF_8.newEncoder().canEncode(value); }
    private String trim(String value) {
        if(value==null) invalid();
        int first=0,last=value.length();
        while(first<last && space(value.codePointAt(first))) first+=Character.charCount(value.codePointAt(first));
        while(last>first && space(value.codePointBefore(last))) last-=Character.charCount(value.codePointBefore(last));
        return value.substring(first,last);
    }
    private boolean space(int c) { return Character.isWhitespace(c)||Character.isSpaceChar(c)||c==0xFEFF; }
    private void invalid() { throw new BusinessException(ErrorCode.INVALID_PARAM,"姓名、邮箱或密码格式不合法"); }
}
