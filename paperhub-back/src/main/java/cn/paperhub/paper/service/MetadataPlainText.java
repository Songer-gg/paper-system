package cn.paperhub.paper.service;

import java.io.*;
import javax.swing.text.*;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;

/** Text extraction only. ParserDelegator never loads document URLs or external entities. */
final class MetadataPlainText {
    private MetadataPlainText() { }
    static String read(String value) throws IOException {
        if (value==null || value.isEmpty() || value.length()>200000) return "";
        StringBuilder output=new StringBuilder();
        // JATS prefixes are markup names, not content; preserve paragraphs before the HTML tokenizer.
        String markup=value.replaceAll("(?is)<(?:script|style)\\b[^>]*>.*?(?:</(?:script|style)\\s*>|\\z)","")
                .replaceAll("(?i)<\\s*(/?)\\s*jats:","<$1")
                .replaceAll("(?i)</(?:p|title|sec|abstract)\\s*>","$0\n");
        new ParserDelegator().parse(new StringReader(markup),new HTMLEditorKit.ParserCallback() {
            int ignored;
            @Override public void handleStartTag(HTML.Tag tag,MutableAttributeSet attributes,int position) {
                if(tag==HTML.Tag.SCRIPT || tag==HTML.Tag.STYLE) ignored++;
                if(ignored==0 && (tag==HTML.Tag.P || tag==HTML.Tag.BR)) output.append('\n');
            }
            @Override public void handleEndTag(HTML.Tag tag,int position) {
                if(tag==HTML.Tag.SCRIPT || tag==HTML.Tag.STYLE) { if(ignored>0) ignored--; }
                if(ignored==0 && (tag==HTML.Tag.P || tag==HTML.Tag.DIV)) output.append('\n');
            }
            @Override public void handleText(char[] text,int position) { if(ignored==0) output.append(text); }
            @Override public void handleSimpleTag(HTML.Tag tag,MutableAttributeSet attributes,int position) {
                if(ignored==0 && tag==HTML.Tag.BR) output.append('\n');
            }
        },true);
        return output.toString().replaceAll("[\\p{Cc}&&[^\\n]]"," ").replace('\u00a0',' ')
                .replaceAll("[\\t ]+"," ").replaceAll(" *\\n+ *","\n").trim();
    }
}
