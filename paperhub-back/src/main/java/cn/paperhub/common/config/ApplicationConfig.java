package cn.paperhub.common.config;

import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.time.Clock;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfig {
    @Bean
    public Clock applicationClock() { return Clock.system(ZoneId.of("Asia/Shanghai")); }

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer timeFormat() {
        return builder -> {
            builder.featuresToDisable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS,
                    com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT);
            DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            builder.serializers(new LocalDateTimeSerializer(format));
            builder.deserializers(new LocalDateTimeDeserializer(format), new StrictStringDeserializer());
        };
    }
}
