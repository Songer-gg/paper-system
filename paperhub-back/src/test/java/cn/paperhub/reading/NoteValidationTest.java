package cn.paperhub.reading;

import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.reading.service.NoteValidation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NoteValidationTest {
    private final NoteValidation validation=new NoteValidation();
    @Test void titleUsesUnicodeCharactersAndRejectsBlankOrMalformedText() {
        String emoji=new String(Character.toChars(0x1F4D6));
        String full=String.join("",java.util.Collections.nCopies(300,emoji));
        assertEquals(full,validation.title("\u3000"+full+"\uFEFF"));
        assertThrows(BusinessException.class,()->validation.title(full+emoji));
        assertThrows(BusinessException.class,()->validation.title("\u3000\uFEFF\u00A0"));
        assertThrows(BusinessException.class,()->validation.title("\uD800"));
    }
    @Test void contentLimitIsUtf8BytesAndMarkdownFormattingIsPreserved() {
        String exact=String.join("",java.util.Collections.nCopies(20000,"文"));
        assertEquals(exact,validation.content(exact));
        assertThrows(BusinessException.class,()->validation.content(exact+"x"));
        String markdown="  indented code\r\n\n<script>literal text</script>  \n";
        assertEquals(markdown,validation.content(markdown));
        assertThrows(BusinessException.class,()->validation.content("\u3000\n "));
    }
    @Test void keywordTreatsWildcardsAsLiteralAndDoesNotBecomeSql() {
        assertEquals("%a!%!_!!%",validation.keyword(" A%_! "));
        assertNull(validation.keyword(" \u3000 "));
        assertEquals("%' or 1=1 --%",validation.keyword("' OR 1=1 --"));
    }
}

