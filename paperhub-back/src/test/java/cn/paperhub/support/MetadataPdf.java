package cn.paperhub.support;

import java.io.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

/** Original synthetic paper fixtures, without any external publication content. */
public final class MetadataPdf {
    public static final String TITLE = "Local Parsing of Scientific Papers";
    private MetadataPdf() { }
    public static byte[] paper(boolean metadata) throws IOException {
        try (PDDocument document=new PDDocument(); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            PDPage page=new PDPage();document.addPage(page);
            if (metadata) {
                document.getDocumentInformation().setTitle(TITLE);
                document.getDocumentInformation().setAuthor("John Smith; Jane Doe");
                document.getDocumentInformation().setKeywords("deep learning; document analysis");
            }
            try (PDPageContentStream content=new PDPageContentStream(document,page)) {
                line(content,TITLE,20,740);line(content,"John Smith, Jane Doe",12,710);
                line(content,"Department of Computer Science, Test University",10,690);
                line(content,"Journal: Test Science",10,670);line(content,"Published: 2025",10,650);
                line(content,"DOI: https://doi.org/10.1234/Local.Parse.",10,630);
                line(content,"Abstract",12,600);line(content,"We study local document parsing without network services.",11,580);
                line(content,"Keywords: deep learning; document analysis",11,550);
                line(content,"1 Introduction",12,520);line(content,"This section must not become part of the abstract.",11,500);
            }
            document.save(output);return output.toByteArray();
        }
    }
    public static void line(PDPageContentStream content,String text,int size,int y) throws IOException {
        content.beginText();content.setFont(PDType1Font.HELVETICA,size);content.newLineAtOffset(50,y);
        content.showText(text);content.endText();
    }
}
