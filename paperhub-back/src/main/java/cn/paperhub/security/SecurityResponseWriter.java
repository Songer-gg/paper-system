package cn.paperhub.security;

import cn.paperhub.common.api.ApiResponse;
import cn.paperhub.common.api.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SecurityResponseWriter {
    private final ObjectMapper mapper;
    public SecurityResponseWriter(ObjectMapper mapper) { this.mapper = mapper; }
    public void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(), ApiResponse.failure(code));
    }
}
