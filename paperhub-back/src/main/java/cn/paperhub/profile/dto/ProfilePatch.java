package cn.paperhub.profile.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import java.util.*;

public class ProfilePatch {
    private String name,email;
    private final Set<String> fields=new HashSet<>();
    public String getName() { return name; }
    @JsonSetter("name") public void setName(String value) { fields.add("name");name=value; }
    public String getEmail() { return email; }
    @JsonSetter("email") public void setEmail(String value) { fields.add("email");email=value; }
    public boolean has(String field) { return fields.contains(field); }
    public boolean empty() { return fields.isEmpty(); }
}
