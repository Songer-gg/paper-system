package cn.paperhub.paper.service;

import cn.paperhub.paper.vo.PaperMetadataView;
import cn.paperhub.support.MetadataPdf;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PaperMetadataExtractorTest {
    final PaperMetadataExtractor extractor = new PaperMetadataExtractor(Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"),ZoneId.of("Asia/Shanghai")));
    PaperMetadataView read(byte[] bytes) throws Exception {
        try (PDDocument document=PDDocument.load(bytes)) { return extractor.extract(document,System.nanoTime()+TimeUnit.SECONDS.toNanos(10)); }
    }
    @Test void metadataAndBodyProduceUsefulCandidateFieldsWithoutSplittingFullNamesOrKeywords() throws Exception {
        PaperMetadataView value=read(MetadataPdf.paper(true));
        assertEquals(MetadataPdf.TITLE,value.title);assertEquals(Arrays.asList("John Smith","Jane Doe"),value.authors);
        assertEquals(Arrays.asList("deep learning","document analysis"),value.keywords);
        assertEquals("10.1234/local.parse",value.doi);assertEquals("Test Science",value.journal);assertEquals(2025,value.year);
        assertEquals("We study local document parsing without network services.",value.abstractText);
        assertEquals("metadata",value.sources.get("title"));assertEquals("text",value.sources.get("abstract"));
    }
    @Test void fontAndHeaderFallbackWorksWhenDocumentMetadataIsAbsent() throws Exception {
        PaperMetadataView value=read(MetadataPdf.paper(false));
        assertEquals(MetadataPdf.TITLE,value.title);assertEquals(Arrays.asList("John Smith","Jane Doe"),value.authors);
        assertEquals("text",value.sources.get("title"));assertEquals("text",value.sources.get("authors"));
        assertFalse(value.warnings.isEmpty());
    }
    @Test void chineseLabelsAndEnglishPhrasesAreParsedConservatively() {
        PaperMetadataView value=new PaperMetadataView();
        extractor.applyText("作者：张三，李四\n期刊：论文解析研究\n出版年份：2024\n摘要：研究本机论文信息提取。\n结果支持人工核对。\n关键词：深度学习；文本解析\n1 引言\n后续正文",value);
        assertEquals(Arrays.asList("张三","李四"),value.authors);assertEquals(2024,value.year);
        assertEquals("研究本机论文信息提取。 结果支持人工核对。",value.abstractText);
        assertEquals(Arrays.asList("深度学习","文本解析"),value.keywords);assertEquals("论文解析研究",value.journal);
        PaperMetadataView dated=new PaperMetadataView();extractor.applyText("Published online: 3 June 2025\nAbstract: A summary.",dated);
        assertEquals(2025,dated.year);
    }
    @Test void spacedBracketedAndPunctuatedAbstractHeadingsAreRecognized() {
        for(String heading:Arrays.asList("A B S T R A C T", "Abstract.", "【摘 要】", "Summary:")) {
            PaperMetadataView value=new PaperMetadataView();
            extractor.applyText(heading+"\nThe original summary is retained.\nKeywords: local parsing\n1 Introduction\nNot the abstract.",value);
            assertEquals("The original summary is retained.",value.abstractText,heading);
        }
    }
    @Test void articleInformationSidebarDoesNotHideOrPolluteTheAbstract() throws Exception {
        try(PDDocument document=new PDDocument()) {
            PDPage page=new PDPage();document.addPage(page);
            try(PDPageContentStream content=new PDPageContentStream(document,page)) {
                textAt(content,"A Two Column Test Paper",20,50,740);
                textAt(content,"ARTICLE INFO",11,50,700);textAt(content,"A B S T R A C T",12,230,700);
                textAt(content,"Keywords:",10,50,675);textAt(content,"We recover the right column abstract.",11,230,675);
                textAt(content,"document analysis",10,50,656);textAt(content,"Position information excludes the sidebar.",11,230,656);
                textAt(content,"Received: 2020",10,50,637);textAt(content,"The full summary stays readable.",11,230,637);
                textAt(content,"1 Introduction",12,230,600);textAt(content,"Body content is excluded.",11,230,580);
            }
            PaperMetadataView value=extractor.extract(document,System.nanoTime()+TimeUnit.SECONDS.toNanos(10));
            assertEquals("We recover the right column abstract. Position information excludes the sidebar. The full summary stays readable.",value.abstractText);
        }
    }
    @Test void abstractInLeftColumnDoesNotIncludeTheRightColumnBody() throws Exception {
        try(PDDocument document=new PDDocument()) {
            PDPage page=new PDPage();document.addPage(page);
            try(PDPageContentStream content=new PDPageContentStream(document,page)) {
                textAt(content,"Abstract",12,50,700);
                for(int i=0;i<3;i++) {
                    textAt(content,"Left column summary sentence.",10,50,675-i*18);
                    textAt(content,"Unrelated right column paragraph.",10,330,675-i*18);
                }
                textAt(content,"Index Terms: local parsing",10,50,610);
            }
            PaperMetadataView value=extractor.extract(document,System.nanoTime()+TimeUnit.SECONDS.toNanos(10));
            assertFalse(value.abstractText.contains("Unrelated"));assertTrue(value.abstractText.startsWith("Left column"));
        }
    }
    @Test void oversizedSummaryIsNotSilentlyDiscarded() {
        PaperMetadataView value=new PaperMetadataView();
        extractor.applyText("Abstract\n"+String.join("",Collections.nCopies(16000,"a"))+"\nKeywords: parsing",value);
        assertEquals(15000,value.abstractText.length());assertTrue(value.warnings.stream().anyMatch(note->note.contains("15000")));
    }
    @Test void unlabelledBoldAbstractBetweenAuthorsAndKeywordsIsRecovered() throws Exception {
        try(PDDocument document=new PDDocument()) {
            PDPage page=new PDPage();document.addPage(page);
            try(PDPageContentStream content=new PDPageContentStream(document,page)) {
                textAt(content,"A Paper Without An Abstract Heading",20,50,740);
                textAt(content,"John Smith, Jane Doe",12,50,710);
                for(int i=0;i<6;i++) {
                    content.beginText();content.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA_BOLD,10);
                    content.newLineAtOffset(50,680-i*14);content.showText("This original summary sentence explains the local scientific experiment.");content.endText();
                }
                textAt(content,"Keywords: local parsing; abstract recovery",10,50,580);
                textAt(content,"The body must not become part of the summary.",10,50,550);
            }
            PaperMetadataView value=extractor.extract(document,System.nanoTime()+TimeUnit.SECONDS.toNanos(10));
            assertTrue(value.abstractText.startsWith("This original summary"));assertFalse(value.abstractText.contains("John Smith"));
            assertFalse(value.abstractText.contains("body must not"));assertTrue(value.warnings.stream().anyMatch(note->note.contains("加粗")));
        }
    }
    private void textAt(PDPageContentStream content,String text,int size,int x,int y) throws Exception {
        content.beginText();content.setFont(org.apache.pdfbox.pdmodel.font.PDType1Font.HELVETICA,size);
        content.newLineAtOffset(x,y);content.showText(text);content.endText();
    }
    @Test void chineseDocumentMetadataIsPreservedWithoutNeedingOcr() throws Exception {
        try(PDDocument document=new PDDocument()) {
            document.addPage(new PDPage());document.getDocumentInformation().setTitle("本机科研论文信息解析");
            document.getDocumentInformation().setAuthor("张三；李四");
            PaperMetadataView value=extractor.extract(document,System.nanoTime()+TimeUnit.SECONDS.toNanos(10));
            assertEquals("本机科研论文信息解析",value.title);assertEquals(Arrays.asList("张三","李四"),value.authors);
        }
    }
    @Test void referenceDoiAndReceivedYearAreNotUsedAsPublicationMetadata() {
        PaperMetadataView value=new PaperMetadataView();
        extractor.applyText("Received: 2022\nAbstract\nA small abstract.\n1 Introduction\nSome content.\nReferences\nDOI: 10.1234/not-this-paper\nPublished: 2023",value);
        assertEquals("",value.doi);assertNull(value.year);assertEquals("A small abstract.",value.abstractText);
    }
    @Test void onlyTheFirstThreePagesAreInspectedAndCreationDateIsNotPublicationYear() throws Exception {
        try (PDDocument document=new PDDocument()) {
            for(int i=0;i<4;i++) document.addPage(new PDPage());
            document.getDocumentInformation().setTitle("A Metadata Only Paper");
            Calendar calendar=Calendar.getInstance();calendar.set(2020,0,1);document.getDocumentInformation().setCreationDate(calendar);
            try (PDPageContentStream content=new PDPageContentStream(document,document.getPage(3))) {
                MetadataPdf.line(content,"DOI: 10.1234/page-four",12,700);MetadataPdf.line(content,"Published: 2021",12,670);
            }
            PaperMetadataView value=extractor.extract(document,System.nanoTime()+TimeUnit.SECONDS.toNanos(10));
            assertEquals(4,value.pageCount);assertEquals(3,value.parsedPages);assertEquals("",value.doi);assertNull(value.year);
        }
    }
    @Test void imageOnlyDocumentsReturnWarningsAndEmptyFieldsWithoutInventingAFileNameTitle() throws Exception {
        try (PDDocument document=new PDDocument()) {
            document.addPage(new PDPage());document.getDocumentInformation().setTitle("Microsoft Word - draft.docx");
            document.getDocumentInformation().setAuthor("Microsoft Office");
            PaperMetadataView value=extractor.extract(document,System.nanoTime()+TimeUnit.SECONDS.toNanos(10));
            assertEquals("",value.title);assertTrue(value.authors.isEmpty());assertTrue(value.warnings.stream().anyMatch(row->row.contains("OCR")));
        }
    }
    @Test void expiredExtractionDeadlineStillReturnsMetadataAndAWarning() throws Exception {
        try (PDDocument document=PDDocument.load(MetadataPdf.paper(true))) {
            PaperMetadataView value=extractor.extract(document,System.nanoTime()-1);
            assertEquals(MetadataPdf.TITLE,value.title);assertTrue(value.abstractText.isEmpty());
            assertTrue(value.warnings.stream().anyMatch(row->row.contains("限制")));
        }
    }
    @Test void textLengthIsBounded() throws Exception {
        try (PDDocument document=new PDDocument()) {
            PDPage page=new PDPage();document.addPage(page);
            try(PDPageContentStream content=new PDPageContentStream(document,page)) {
                MetadataPdf.line(content,String.join("",Collections.nCopies(81000,"a")),10,700);
            }
            PaperMetadataView value=extractor.extract(document,System.nanoTime()+TimeUnit.SECONDS.toNanos(10));
            assertTrue(value.warnings.stream().anyMatch(row->row.contains("限制")));assertTrue(value.abstractText.length()<=15000);
        }
    }
    @Test void multiLineTitleIsJoinedAndAbstractLikeWordsAreNotSectionLabels() throws Exception {
        try(PDDocument document=new PDDocument()) {
            PDPage page=new PDPage();document.addPage(page);
            try(PDPageContentStream content=new PDPageContentStream(document,page)) {
                MetadataPdf.line(content,"Abstracting Scientific Knowledge",20,740);MetadataPdf.line(content,"from Local Documents",20,710);
                MetadataPdf.line(content,"John Smith and Jane Doe",12,680);MetadataPdf.line(content,"Abstract: Original summary.",11,630);
                MetadataPdf.line(content,"Index Terms: document analysis, machine learning",11,600);
            }
            PaperMetadataView value=extractor.extract(document,System.nanoTime()+TimeUnit.SECONDS.toNanos(10));
            assertEquals("Abstracting Scientific Knowledge from Local Documents",value.title);
            assertEquals(Arrays.asList("John Smith","Jane Doe"),value.authors);
        }
    }
    @Test void explicitFirstPageDoiFooterCanBeUsedWithoutFetchingItsUrl() throws Exception {
        try(PDDocument document=new PDDocument()) {
            PDPage page=new PDPage();document.addPage(page);
            try(PDPageContentStream content=new PDPageContentStream(document,page)) {
                MetadataPdf.line(content,"Abstract: A local test.",12,700);MetadataPdf.line(content,"1 Introduction",12,660);
                MetadataPdf.line(content,"https://doi.org/10.1234/FOOTER",10,50);
            }
            PaperMetadataView value=extractor.extract(document,System.nanoTime()+TimeUnit.SECONDS.toNanos(10));
            assertEquals("10.1234/footer",value.doi);
        }
    }
}
