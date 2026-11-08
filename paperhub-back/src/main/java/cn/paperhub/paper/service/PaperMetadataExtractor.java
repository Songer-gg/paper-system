package cn.paperhub.paper.service;

import cn.paperhub.paper.vo.PaperMetadataView;
import java.io.*;
import java.time.Clock;
import java.time.Year;
import java.util.*;
import java.util.regex.*;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.text.*;
import org.springframework.stereotype.Component;

/** Conservative local heuristics. Missing evidence yields an empty field, never fabricated metadata. */
@Component
public class PaperMetadataExtractor {
    private static final Pattern ABSTRACT = Pattern.compile("(?iu)^\\s*(?:[0-9IV一]+[.、)]?\\s+)?[\\[【（]?\\s*(?:a\\s*b\\s*s\\s*t\\s*r\\s*a\\s*c\\s*t(?!\\p{L})|summary\\b|(?:内容)?摘\\s*要|概要)\\s*[\\]】）]?\\s*[:：.。—–-]?\\s*(.*)$");
    private static final Pattern KEYWORDS = Pattern.compile("(?iu)^\\s*(?:keywords?\\b|key\\s+words\\b|index\\s+terms\\b|关键词|关键字)\\s*[:：—–-]?\\s*(.*)$");
    private static final Pattern STOP = Pattern.compile("(?iu)^\\s*(?:(?:[1I][.、)]?\\s*)?(?:introduction|引言|绪论)|references|参考文献|acknowledg(?:e)?ments?)\\s*[:：.]?\\s*.*$");
    private static final Pattern DOI = Pattern.compile("(?i)10\\.\\d{4,9}/[-._;()/:a-z0-9]+(?:[-._;()/:a-z0-9]*)");
    private static final Pattern INSTITUTION = Pattern.compile("(?iu).*(?:\\b(?:university|department|school|laboratory|institute|college|centre|center|received|accepted|published|copyright|journal|conference)\\b|大学|学院|研究所|实验室|通讯作者|@|https?://).*" );
    private final Clock clock;
    public PaperMetadataExtractor(Clock clock) { this.clock = clock; }

    public PaperMetadataView extract(PDDocument document, long deadline) throws IOException {
        PaperMetadataView result = new PaperMetadataView();
        result.pageCount = document.getNumberOfPages(); result.parsedPages = Math.min(3, result.pageCount);
        PDDocumentInformation info = document.getDocumentInformation();
        String title = clean(info.getTitle());
        if (titleCandidate(title)) { result.title = title; result.sources.put("title", "metadata"); }
        result.authors = authors(info.getAuthor(), false);
        if (!result.authors.isEmpty()) result.sources.put("authors", "metadata");
        result.keywords = keywords(info.getKeywords());
        if (!result.keywords.isEmpty()) result.sources.put("keywords", "metadata");
        BoundedText reader = new BoundedText(deadline);
        reader.setStartPage(1); reader.setEndPage(result.parsedPages); reader.setSortByPosition(true);
        StringWriter output = new StringWriter();
        try { reader.writeText(document, output); }
        catch (ExtractionLimit ex) { result.warnings.add("文字提取达到时间或长度限制，部分信息可能缺失"); }
        reader.flushLine();
        String text = output.toString();
        applyText(text, result);
        applyLayout(reader.runs,document,result);
        if(result.abstractText.isEmpty()) inferUnlabelledAbstract(reader.runs,result);
        if (result.doi.isEmpty()) {
            for (Line line : reader.lines) {
                if (line.page != 1 || line.text.matches("(?iu)^(?:references|参考文献)\\s*$")) break;
                if (!line.text.matches("(?iu).*(?:doi\\s*[:：]|doi\\.org/).*")) continue;
                String value = doi(line.text);
                if (!value.isEmpty()) { result.doi=value; result.sources.put("doi","text"); break; }
            }
        }
        if (result.title.isEmpty()) {
            List<Line> titleRows=new ArrayList<>();
            for(Run row:reader.runs) titleRows.add(new Line(row.text,row.font,row.y,row.page));
            inferTitle(titleRows, result);
        }
        if (result.authors.isEmpty()) inferAuthors(reader.lines, result);
        if (clean(text).isEmpty()) result.warnings.add("未提取到正文文字；扫描件需要OCR，请手动补充信息");
        if (result.title.isEmpty() || result.authors.isEmpty() || result.abstractText.isEmpty())
            result.warnings.add("部分论文信息未识别，请补充必填项");
        return result;
    }

    void applyText(String text, PaperMetadataView result) {
        List<String> lines = new ArrayList<>();
        // References are never a source of the uploaded paper's DOI, year, or authors.
        for (String raw : text.split("\\R")) {
            String line = clean(raw);
            if (line.matches("(?iu)^(?:[0-9]+[.、]?\\s*)?(?:references|参考文献)\\s*[:：]?$")) break;
            if (!line.isEmpty()) lines.add(line);
        }
        for (String line : lines) {
            Matcher header = Pattern.compile("(?iu)^(?:authors?|作者)\\s*[:：]\\s*(.+)$").matcher(line);
            if (result.authors.isEmpty() && header.matches()) {
                List<String> names = authors(header.group(1), false);
                if (!names.isEmpty()) { result.authors = names; result.sources.put("authors", "text"); }
            }
        }
        boolean inAbstract = false;
        List<String> abstractLines = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i); Matcher label = ABSTRACT.matcher(line), keys = KEYWORDS.matcher(line);
            if (label.matches()) {
                if (inAbstract) break;
                inAbstract = true; if (!label.group(1).isEmpty()) abstractLines.add(label.group(1));
            } else if (inAbstract && (keys.matches() || STOP.matcher(line).matches())) break;
            else if (inAbstract) abstractLines.add(line);
        }
        String summary = clean(String.join(" ", abstractLines));
        if (!summary.isEmpty()) {
            result.abstractText = boundedAbstract(summary,result); result.sources.put("abstract", "text");
        }
        for (int i = 0; i < lines.size(); i++) {
            Matcher keys = KEYWORDS.matcher(lines.get(i));
            if (keys.matches() && result.keywords.isEmpty()) {
                String value = keys.group(1);
                if (value.isEmpty() && i + 1 < lines.size() && !STOP.matcher(lines.get(i+1)).matches()) value = lines.get(i+1);
                List<String> words = keywords(value);
                if (!words.isEmpty()) { result.keywords = words; result.sources.put("keywords", "text"); }
            }
        }
        // Limit bibliographic guesses to the header before the abstract/introduction.
        for (String line : lines) {
            if (ABSTRACT.matcher(line).matches() || STOP.matcher(line).matches()) break;
            if (result.doi.isEmpty()) {
                String value=doi(line);
                if (!value.isEmpty()) { result.doi=value; result.sources.put("doi","text"); }
            }
            Matcher journal = Pattern.compile("(?iu)^(?:journal|期刊|刊物)\\s*[:：]\\s*(.{2,255})$").matcher(line);
            if (result.journal.isEmpty() && journal.matches()) { result.journal = journal.group(1); result.sources.put("journal", "text"); }
            Matcher year = Pattern.compile("(?iu)(?:published\\b(?:\\s+(?:online|in|on))?|publication\\s+year\\b|year\\b|发表年份|出版年份|发表时间|出版年)\\s*[:：]?\\s*[\\p{L}\\d\\s,./-]{0,40}?((?:19|20)\\d{2})\\b").matcher(line);
            if (result.year == null && year.find()) {
                int value = Integer.parseInt(year.group(1));
                if (value <= Year.now(clock).getValue()+1) { result.year = value; result.sources.put("year", "text"); }
            }
        }
    }

    private void applyLayout(List<Run> runs,PDDocument document,PaperMetadataView result) {
        for(int at=0;at<runs.size();at++) {
            Run heading=runs.get(at);Matcher label=ABSTRACT.matcher(heading.text);
            if(!label.matches()) continue;
            float left=heading.x;
            for(int i=at+1;i<runs.size();i++) {
                Run row=runs.get(i);
                if(row.page!=heading.page || row.y>heading.y+65) break;
                if(row.y>heading.y+3 && row.text.length()>25 && row.x<=heading.x+20 && row.end>heading.x+30
                        && !KEYWORDS.matcher(row.text).matches() && !STOP.matcher(row.text).matches()) {
                    left=Math.min(left,row.x);break;
                }
            }
            float width=document.getPage(heading.page-1).getMediaBox().getWidth(),right=width;
            int sidebar=0;Map<Integer,List<Float>> rightColumns=new TreeMap<>();
            for(Run row:runs) if(row.page==heading.page && row.y>heading.y && row.y<heading.y+220) {
                if(row.x<left-45) sidebar++;
                if(row.x>Math.max(left+120,width*0.42f) && row.text.length()>15)
                    rightColumns.computeIfAbsent(Math.round(row.x/20),key->new ArrayList<>()).add(row.x);
            }
            if(sidebar<3) for(List<Float> cluster:rightColumns.values()) if(cluster.size()>=3) {
                right=Collections.min(cluster)-8;break;
            }
            List<String> body=new ArrayList<>();
            if(!label.group(1).isEmpty()) body.add(label.group(1));
            for(int i=at+1;i<runs.size();i++) {
                Run row=runs.get(i);
                if(row.page!=heading.page) break;
                if(row.y<=heading.y+2 || row.x<left-16 || row.x>=right) continue;
                if(KEYWORDS.matcher(row.text).matches() || STOP.matcher(row.text).matches() || ABSTRACT.matcher(row.text).matches()) break;
                // A numbered heading also ends unstructured abstracts such as "1 Background".
                if(row.text.matches("(?iu)^(?:[1I一][.、)]?\\s+)[\\p{L}].{1,100}$") && row.font>=heading.font*0.9f) break;
                body.add(row.text);
            }
            String summary=clean(String.join(" ",body));
            if(!summary.isEmpty()) {
                result.abstractText=boundedAbstract(summary,result);result.sources.put("abstract","text");return;
            }
        }
    }
    private String boundedAbstract(String text,PaperMetadataView result) {
        if(text.length()<=15000) return text;
        if(!result.warnings.contains("摘要超过自动识别长度，已保留前15000字符，请核对结束位置"))
            result.warnings.add("摘要超过自动识别长度，已保留前15000字符，请核对结束位置");
        int end=15000;if(Character.isHighSurrogate(text.charAt(end-1))) end--;
        return text.substring(0,end);
    }
    private void inferUnlabelledAbstract(List<Run> runs,PaperMetadataView result) {
        for(int key=0;key<runs.size();key++) {
            Run keywords=runs.get(key);
            if(keywords.page!=1 || !KEYWORDS.matcher(keywords.text).matches()) continue;
            LinkedList<String> body=new LinkedList<>();Run next=keywords;Float size=null;
            for(int i=key-1;i>=0;i--) {
                Run row=runs.get(i);
                if(row.page!=1) break;
                if(Math.abs(row.x-keywords.x)>18) continue;
                if(next.y-row.y>Math.max(30,row.font*3) || row.font<7 || row.font>16
                        || row.boldCharacters<row.text.replace(" ","").length()*0.7f) break;
                if(size!=null && Math.abs(row.font-size)>0.7f) break;
                List<String> names=authors(row.text,true);
                boolean authorLine=!names.isEmpty() && names.stream().allMatch(name->name.matches("[\\p{IsHan}]{2,6}") || Character.isUpperCase(name.codePointAt(0)))
                        && !row.text.endsWith(".");
                if(authorLine || INSTITUTION.matcher(row.text).matches()) break;
                body.addFirst(row.text);next=row;size=row.font;
            }
            String summary=clean(String.join(" ",body));
            if(body.size()>=3 && summary.length()>=200) {
                result.abstractText=boundedAbstract(summary,result);result.sources.put("abstract","text");
                result.warnings.add("摘要来自关键词上方加粗段落推测，请核对");return;
            }
        }
    }

    private void inferTitle(List<Line> rows, PaperMetadataView result) {
        int chosen = -1; float largest = 0;
        for (int i = 0; i < rows.size(); i++) {
            Line row = rows.get(i);
            if (row.page != 1 || row.y > 450 || ABSTRACT.matcher(row.text).matches()) break;
            if (titleCandidate(row.text) && !INSTITUTION.matcher(row.text).matches() && row.font > largest) {
                chosen = i; largest = row.font;
            }
        }
        if (chosen < 0) return;
        StringBuilder title = new StringBuilder(rows.get(chosen).text);
        Line previous = rows.get(chosen);
        for (int i = chosen+1; i < Math.min(chosen+3, rows.size()); i++) {
            Line next = rows.get(i);
            if (next.page != 1 || next.font < largest*0.85 || next.y-previous.y > largest*2.3
                    || !titleCandidate(next.text) || INSTITUTION.matcher(next.text).matches()) break;
            if (title.length()+next.text.length()+1 > 255) break;
            title.append(' ').append(next.text); previous = next;
        }
        result.title = title.toString(); result.sources.put("title", "text");
        result.warnings.add("题目来自首页版式推测，请核对");
    }

    private void inferAuthors(List<Line> rows, PaperMetadataView result) {
        boolean afterTitle = false;
        for (Line row : rows) {
            if (row.page != 1 || ABSTRACT.matcher(row.text).matches()) break;
            if (result.title.contains(row.text)) { afterTitle = true; continue; }
            if (!afterTitle || INSTITUTION.matcher(row.text).matches()) continue;
            List<String> names = authors(row.text, true);
            if (!names.isEmpty()) {
                result.authors = names; result.sources.put("authors", "text");
                result.warnings.add("作者来自首页文字推测，请核对姓名及分隔"); return;
            }
        }
    }

    private static boolean titleCandidate(String value) {
        return value.length() >= 4 && value.length() <= 255 && !value.matches("(?iu).*(?:\\.pdf|\\.docx?|microsoft|untitled|无标题|latex|adobe|arxiv:|https?://|doi\\s*:|\\b(?:abstract|keywords|volume|issn|open access)\\b).*" )
                && value.codePoints().anyMatch(Character::isLetter) && !value.matches("(?iu)^(?:title|document|标题|摘要|contents)$");
    }
    private static List<String> authors(String text, boolean guessed) {
        List<String> result = new ArrayList<>();
        String value = clean(text);
        if (value.isEmpty() || value.length() > 6000 || INSTITUTION.matcher(value).matches()
                || value.matches("(?iu).*(?:microsoft|adobe|latex|unknown|anonymous|作者未详).*")) return result;
        for (String part : value.split("(?iu)\\s+(?:and|&)\\s+|[,，;；、]")) {
            String name = clean(part.replaceAll("[0-9¹²³⁴⁵⁶⁷⁸⁹⁰*†‡\\p{Co}\\p{So}]+", ""));
            if (name.isEmpty() || name.length() > 120 || !name.matches("[\\p{L}][\\p{L}\\p{M}.'’\\-]*(?:\\s+[\\p{L}][\\p{L}\\p{M}.'’\\-]*){0,4}")) return new ArrayList<>();
            if (guessed && !name.contains(" ") && !name.matches("[\\p{IsHan}]{2,6}")) return new ArrayList<>();
            if (!result.contains(name)) result.add(name);
        }
        return result.size() <= 50 ? result : new ArrayList<>();
    }
    private static List<String> keywords(String text) {
        List<String> result = new ArrayList<>(); String value = clean(text);
        if (value.isEmpty() || value.length() > 6000) return result;
        for (String part : value.split("[,，;；、]")) {
            String word = clean(part);
            if (word.isEmpty() || word.length() > 120 || word.contains("http")) return new ArrayList<>();
            if (!result.contains(word)) result.add(word);
        }
        return result.size() <= 50 ? result : new ArrayList<>();
    }
    private static String clean(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("[\\p{Z}\\s]+", " ").trim();
    }
    private static int count(String value, char c) { int count=0; for(char ch:value.toCharArray()) if(ch==c) count++; return count; }
    private static String doi(String text) {
        Matcher match=DOI.matcher(text); if (!match.find()) return "";
        String value=match.group().replaceFirst("[.;,:]+$", "");
        while (value.endsWith(")") && count(value,')')>count(value,'(')) value=value.substring(0,value.length()-1);
        return value.length()<=120 ? value.toLowerCase(Locale.ROOT) : "";
    }
    private static final class Line {
        final String text; final float font,y; final int page;
        Line(String text,float font,float y,int page) { this.text=text;this.font=font;this.y=y;this.page=page; }
    }
    private static final class Run {
        final String text;final float x,end,y,font;final int page,boldCharacters;
        Run(String text,float x,float end,float y,float font,int page,int boldCharacters) { this.text=text;this.x=x;this.end=end;this.y=y;this.font=font;this.page=page;this.boldCharacters=boldCharacters; }
    }
    private static final class ExtractionLimit extends IOException { }
    private static final class BoundedText extends PDFTextStripper {
        final List<Line> lines = new ArrayList<>();
        final List<Run> runs=new ArrayList<>(),fragments=new ArrayList<>();
        final long deadline; int characters; final StringBuilder line = new StringBuilder(); float font,y;
        BoundedText(long deadline) throws IOException { this.deadline=deadline; }
        void check() throws ExtractionLimit { if (System.nanoTime()>deadline || characters>80000 || Thread.currentThread().isInterrupted()) throw new ExtractionLimit(); }
        @Override protected void processOperator(Operator operator,List<COSBase> operands) throws IOException {
            check(); super.processOperator(operator,operands);
        }
        @Override protected void writeString(String text,List<TextPosition> positions) throws IOException {
            characters+=text.length(); check();
            if (line.length()>0) line.append(' '); line.append(text);
            for (TextPosition position:positions) { font=Math.max(font,position.getFontSizeInPt()); y=position.getYDirAdj(); }
            if(!positions.isEmpty()) {
                TextPosition first=positions.get(0),last=positions.get(positions.size()-1);
                int strong=0;
                for(TextPosition position:positions) {
                    String name=position.getFont().getName(),unicode=position.getUnicode();
                    if(name!=null && unicode!=null && name.matches("(?i).*(?:bold|black|demi).*")) strong+=unicode.length();
                }
                fragments.add(new Run(clean(text),first.getXDirAdj(),last.getXDirAdj()+last.getWidthDirAdj(),first.getYDirAdj(),first.getFontSizeInPt(),getCurrentPageNo(),strong));
            }
            super.writeString(text,positions);
        }
        void flushLine() {
            String value=clean(line.toString()); if (!value.isEmpty()) lines.add(new Line(value,font,y,getCurrentPageNo()));
            fragments.sort(Comparator.comparingDouble(row->row.x));Run combined=null;
            for(Run part:fragments) {
                if(combined==null) combined=part;
                else if(part.x-combined.end>16 || (part.x-combined.end>3 && (part.font>combined.font*1.3f || combined.font>part.font*1.3f))) { runs.add(combined);combined=part; }
                else combined=new Run(combined.text+" "+part.text,combined.x,Math.max(combined.end,part.end),combined.y,Math.max(combined.font,part.font),combined.page,combined.boldCharacters+part.boldCharacters);
            }
            if(combined!=null) runs.add(combined);fragments.clear();
            line.setLength(0);font=0;y=0;
        }
        @Override protected void writeLineSeparator() throws IOException { flushLine(); super.writeLineSeparator(); }
        @Override protected void writePageEnd() throws IOException { flushLine(); super.writePageEnd(); }
    }
}
