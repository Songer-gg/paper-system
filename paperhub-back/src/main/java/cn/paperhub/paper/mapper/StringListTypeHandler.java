package cn.paperhub.paper.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

public class StringListTypeHandler extends BaseTypeHandler<List<String>> {
    private static final ObjectMapper JSON = new ObjectMapper();
    @Override public void setNonNullParameter(PreparedStatement ps, int i, List<String> value, JdbcType type) throws SQLException {
        try { ps.setString(i, JSON.writeValueAsString(value)); }
        catch (Exception ex) { throw new SQLException("Invalid JSON array", ex); }
    }
    private List<String> decode(String value) throws SQLException {
        if (value == null) return Collections.emptyList();
        try {
            JsonNode node = JSON.readTree(value);
            if (!node.isArray()) throw new IllegalArgumentException();
            List<String> result = new ArrayList<>();
            for (JsonNode item : node) {
                if (!item.isTextual()) throw new IllegalArgumentException();
                result.add(item.asText());
            }
            return result;
        } catch (Exception ex) { throw new SQLException("Invalid stored JSON array", ex); }
    }
    @Override public List<String> getNullableResult(ResultSet rs, String column) throws SQLException { return decode(rs.getString(column)); }
    @Override public List<String> getNullableResult(ResultSet rs, int column) throws SQLException { return decode(rs.getString(column)); }
    @Override public List<String> getNullableResult(CallableStatement cs, int column) throws SQLException { return decode(cs.getString(column)); }
}

