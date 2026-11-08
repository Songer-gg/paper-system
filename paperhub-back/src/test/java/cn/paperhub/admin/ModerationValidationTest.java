package cn.paperhub.admin;

import cn.paperhub.admin.service.ModerationValidation;
import cn.paperhub.common.exception.BusinessException;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModerationValidationTest {
    final ModerationValidation validation=new ModerationValidation();
    @Test void reportResultsUseUnicodeCharactersAndRejectBlankOrInvalidText() {
        String emoji=new String(Character.toChars(0x1F600));
        String limit=String.join("",Collections.nCopies(500,emoji));
        assertEquals(limit,validation.result("\u3000"+limit+"\uFEFF"));
        assertThrows(BusinessException.class,()->validation.result(limit+emoji));
        for(String bad:new String[]{null,""," \u3000\uFEFF","\uD800","value\u0000"})
            assertThrows(BusinessException.class,()->validation.result(bad));
    }
    @Test void actionNamesAreExactBoundedStrings() {
        assertEquals("敏感数据访问",validation.action(" 敏感数据访问 "));
        assertEquals("%_!' OR 1=1",validation.action("%_!' OR 1=1"));
        assertThrows(BusinessException.class,()->validation.action(String.join("",Collections.nCopies(51,"a"))));
        assertThrows(BusinessException.class,()->validation.action("\u3000"));
    }
}
