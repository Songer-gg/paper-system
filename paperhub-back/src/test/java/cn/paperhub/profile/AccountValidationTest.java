package cn.paperhub.profile;

import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.profile.service.AccountValidation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountValidationTest {
    final AccountValidation validation=new AccountValidation();
    @Test void namesUseUnicodeCharactersAndEmailsAreNormalized() {
        String emoji=new String(Character.toChars(0x1F600));
        assertEquals(String.join("",java.util.Collections.nCopies(50,emoji)),validation.name(" \u3000"+String.join("",java.util.Collections.nCopies(50,emoji))+" "));
        assertThrows(BusinessException.class,()->validation.name(String.join("",java.util.Collections.nCopies(51,emoji))));
        assertEquals("research+tag@example.com",validation.email(" Research+Tag@Example.COM "));
        for(String bad:new String[]{"","a@b","a..b@example.com","a@-domain.com","a@domain..com","a b@example.com"})
            assertThrows(BusinessException.class,()->validation.email(bad));
        assertThrows(BusinessException.class,()->validation.name("\u3000\uFEFF"));
        assertThrows(BusinessException.class,()->validation.name("\uD800"));
    }
    @Test void passwordLimitsAreUtf8BytesAndNeverTrimPasswords() {
        validation.password("123456",false);validation.password(" password ",true);
        validation.password(String.join("",java.util.Collections.nCopies(24,"密")),true);
        assertThrows(BusinessException.class,()->validation.password(String.join("",java.util.Collections.nCopies(25,"密")),true));
        for(String bad:new String[]{"1234567","        ","password\u0000","\uD800abcde123"})
            assertThrows(BusinessException.class,()->validation.password(bad,true));
        assertEquals("%!%!_!!%",validation.keyword(" %_! "));
    }
}
