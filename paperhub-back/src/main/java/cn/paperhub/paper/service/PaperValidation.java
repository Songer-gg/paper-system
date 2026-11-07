package cn.paperhub.paper.service;

import cn.paperhub.common.api.ErrorCode;
import cn.paperhub.common.exception.BusinessException;
import cn.paperhub.paper.entity.PaperEntity;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class PaperValidation {
    public static final long MAX_ID = 9007199254740991L;
    public static void invalid() { throw new BusinessException(ErrorCode.INVALID_PARAM); }
    public static void id(Long id) { if (id == null || id < 1 || id > MAX_ID) invalid(); }
    private String text(String value, int max, boolean required) {
        if (value == null) { if (required) invalid(); return null; }
        String clean = value.trim();
        if (clean.length() > max || (required && clean.isEmpty())) invalid();
        return clean;
    }
    private List<String> strings(List<String> values, boolean nonempty) {
        if (values == null || values.size() > 50 || (nonempty && values.isEmpty())) invalid();
        List<String> result = new ArrayList<>();
        for (String value : values) result.add(text(value, 120, true));
        return result;
    }
    public String normalizeDoi(String value) {
        String clean = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        clean = clean.replaceFirst("^(?:https?://(?:dx\\.)?doi\\.org/|doi:\\s*)", "").trim();
        if (clean.isEmpty()) return null;
        if (clean.length() > 120 || !clean.matches("10\\.\\d{4,9}/[!-~]+")) invalid();
        return clean;
    }
    public void validate(PaperEntity paper, List<Long> tags) {
        paper.setTitle(text(paper.getTitle(), 255, true));
        paper.setAuthors(strings(paper.getAuthors(), true));
        paper.setAbstractText(text(paper.getAbstractText(), 60000, true));
        if (paper.getAbstractText().getBytes(StandardCharsets.UTF_8).length > 60000) invalid();
        paper.setDoi(text(paper.getDoi(), 120, false));
        paper.setDoiNormalized(normalizeDoi(paper.getDoi()));
        paper.setJournal(text(paper.getJournal(), 255, false));
        paper.setKeywords(strings(paper.getKeywords(), false));
        if (paper.getYear() == null || paper.getYear() < 1000 || paper.getYear() > 9999) invalid();
        if (paper.getCategoryId() != null) id(paper.getCategoryId());
        if (tags == null || tags.size() > 50 || new HashSet<>(tags).size() != tags.size()) invalid();
        for (Long tag : tags) id(tag);
        String link = text(paper.getLink(), 500, false);
        if (link != null && !link.isEmpty()) {
            try {
                URI uri = new URI(link);
                if (!(uri.getScheme().equalsIgnoreCase("https") || uri.getScheme().equalsIgnoreCase("http"))
                        || uri.getHost() == null || uri.getUserInfo() != null) invalid();
            } catch (Exception ex) { invalid(); }
        }
        paper.setLink(link == null || link.isEmpty() ? null : link);
    }
}

