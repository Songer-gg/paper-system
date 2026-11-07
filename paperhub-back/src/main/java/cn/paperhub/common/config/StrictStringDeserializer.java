package cn.paperhub.common.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import java.io.IOException;

/** Jackson 2.13's StringDeserializer still converts array numbers to text; reject that explicitly. */
public final class StrictStringDeserializer extends StdScalarDeserializer<String> {
    public StrictStringDeserializer() { super(String.class); }
    @Override public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_STRING)) return parser.getText();
        return (String) context.handleUnexpectedToken(String.class, parser);
    }
}
