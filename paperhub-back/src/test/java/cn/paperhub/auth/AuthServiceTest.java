package cn.paperhub.auth;

import cn.paperhub.auth.dto.LoginRequest;
import cn.paperhub.auth.entity.UserAccount;
import cn.paperhub.auth.mapper.UserAccountMapper;
import cn.paperhub.auth.service.AuthService;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.security.JwtService;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    @Test void missingWrongRoleDisabledAndWrongPasswordHaveSameFailureMessage() {
        UserAccountMapper mapper = mock(UserAccountMapper.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
        AuthService auth = new AuthService(mapper, encoder, mock(JwtService.class));
        LoginRequest request = new LoginRequest();
        request.setUsername("researcher"); request.setPassword("wrong"); request.setRole("user");
        when(mapper.findByLogin(anyString())).thenReturn(Collections.emptyList());
        String missing = assertThrows(BusinessException.class, () -> auth.login(request)).getMessage();
        UserAccount user = new UserAccount();
        user.setPassword(encoder.encode("correct")); user.setStatus("active"); user.setRole("user");
        when(mapper.findByLogin(anyString())).thenReturn(Collections.singletonList(user));
        assertEquals(missing, assertThrows(BusinessException.class, () -> auth.login(request)).getMessage());
        request.setPassword("correct"); request.setRole("admin");
        assertEquals(missing, assertThrows(BusinessException.class, () -> auth.login(request)).getMessage());
        request.setRole("user"); user.setStatus("disabled");
        assertEquals(missing, assertThrows(BusinessException.class, () -> auth.login(request)).getMessage());
        when(mapper.findByLogin(anyString())).thenReturn(Arrays.asList(user, user));
        assertEquals(missing, assertThrows(BusinessException.class, () -> auth.login(request)).getMessage());
    }
    @Test void utf8PasswordLengthIsCheckedBeforeBcrypt() {
        AuthService auth = new AuthService(mock(UserAccountMapper.class), new BCryptPasswordEncoder(4), mock(JwtService.class));
        LoginRequest request = new LoginRequest();
        request.setUsername("user"); request.setRole("user");
        request.setPassword(String.join("", Collections.nCopies(25, "密")));
        assertThrows(BusinessException.class, () -> auth.login(request));
    }
}
