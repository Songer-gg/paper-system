package cn.paperhub.reading.service;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class NoteValidation {
    public String title(String input) {
        String clean=trim(input);
        if(clean.isEmpty() || clean.codePointCount(0,clean.length())>300 || !validUnicode(clean)) invalid();
        return clean;
    }
    /** Preserve Markdown indentation/newlines; validation must not silently rewrite the user's body. */
    public String content(String input) {
        if(input==null || trim(input).isEmpty() || !validUnicode(input) ||
                input.getBytes(StandardCharsets.UTF_8).length>60000) invalid();
        return input;
    }
    public String keyword(String input) {
        if(input==null) return null;
        String clean=trim(input);
        if(clean.codePointCount(0,clean.length())>300 || !validUnicode(clean)) invalid();
        if(clean.isEmpty()) return null;
        return "%"+clean.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
    }
    private String trim(String value) {
        if(value==null) invalid();
        int start=0,end=value.length();
        while(start<end && space(value.codePointAt(start))) start+=Character.charCount(value.codePointAt(start));
        while(end>start && space(value.codePointBefore(end))) end-=Character.charCount(value.codePointBefore(end));
        return value.substring(start,end);
    }
    private boolean space(int c) { return Character.isWhitespace(c)||Character.isSpaceChar(c)||c==0xFEFF; }
    private boolean validUnicode(String input) { return StandardCharsets.UTF_8.newEncoder().canEncode(input); }
    private void invalid() { throw new BusinessException(ErrorCode.INVALID_PARAM,"笔记标题或正文为空、超长或编码不合法"); }
}

