package cn.paperhub.knowledge.dto;

import javax.validation.constraints.*;

public class TagRequest {
    @NotBlank @Size(max=50)
    private String name;
    @NotBlank @Pattern(regexp="#[0-9a-fA-F]{6}")
    private String color;
    public String getName() { return name; }
    public void setName(String name) { this.name=name; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color=color; }
}

