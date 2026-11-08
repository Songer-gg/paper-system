package cn.paperhub.paper;

import cn.paperhub.support.SharingSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="paperhub.storage.cleanup-enabled=false")
@AutoConfigureMockMvc
class SharedSearchIT extends SharingSupport {
    @Test void searchCoversTitleAuthorsAbstractDoiJournalAndKeywordsWithoutExposingPrivateData() throws Exception {
        String marker=unique();
        ObjectNode p=base("shared");
        p.put("title","Title-"+marker);p.put("abstract","Abstract-"+marker);p.put("doi","10.9999/"+marker);
        p.put("journal","Journal-"+marker);p.putArray("authors").add("Author-"+marker);p.putArray("keywords").add("Keyword-"+marker);
        long id=publish(p,user);
        ObjectNode hidden=p.deepCopy();hidden.put("visibility","private");hidden.put("copyrightAgreed",false);create(hidden,other);
        for(String term:Arrays.asList("title-"+marker,"AUTHOR-"+marker,"Abstract-"+marker,
                "10.9999/"+marker,"JOURNAL-"+marker,"keyword-"+marker)) {
            JsonNode result=search("q",term);
            assertEquals(1,result.path("total").asLong(),term);
            assertEquals(Collections.singleton(id),ids(result.path("list")),term);
        }
        JsonNode visible=read("/shared-papers/"+id,other);
        assertFalse(visible.has("filePath"));assertFalse(visible.has("fileSha256"));assertFalse(visible.has("reviewComment"));
    }
    @Test void sharedPoolAlwaysExcludesOwnPrivatePendingRejectedOfflineAndDeletedPapers() throws Exception {
        String marker=unique();ObjectNode p=base("shared");p.put("abstract",marker);
        long approved=publish(p,user);
        long pending=create(p.deepCopy().put("title","Pending "+unique()),user);
        ObjectNode secret=p.deepCopy().put("title","Private "+unique()).put("visibility","private").put("copyrightAgreed",false);
        long privateId=create(secret,user);
        long rejected=create(p.deepCopy().put("title","Rejected "+unique()),other);
        ok(api("POST","/admin/review/papers/"+rejected+"/reject",body("comment","reject"),admin));
        long offline=create(p.deepCopy().put("title","Offline "+unique()),user);ok(flow(offline,"unshare",null,user));
        long deleted=publish(p.deepCopy().put("title","Deleted "+unique()),other);ok(api("DELETE","/admin/papers/"+deleted,null,admin));
        JsonNode result=search("q",marker);
        assertEquals(1,result.path("total").asLong());assertEquals(Collections.singleton(approved),ids(result.path("list")));
        for(long id:new long[]{pending,privateId,rejected,offline,deleted}) {
            api("GET","/shared-papers/"+id,null,user).andExpect(status().isNotFound());
            api("GET","/shared-papers/"+id,null,admin).andExpect(status().isNotFound());
        }
        assertEquals("pending",read("/admin/papers/"+pending,admin).path("reviewStatus").asText());
    }
    @Test void filtersUseDescendantsTagOrSemanticsAndDoNotMultiplyTheTotal() throws Exception {
        long root=ok(api("POST","/categories",body("name",unique(),"parentId",null),admin)).path("id").asLong();
        long child=ok(api("POST","/categories",body("name",unique(),"parentId",root),admin)).path("id").asLong();
        long ta=tag(),tb=tag(),tc=tag();String marker=unique(),journal="Journal "+unique();
        ObjectNode one=base("shared");one.put("categoryId",child);one.put("abstract",marker);one.put("year",2024);one.put("journal",journal);
        one.putArray("tagIds").add(ta).add(tb);long a=publish(one,user);
        ObjectNode two=base("shared");two.put("categoryId",root);two.put("abstract",marker);two.put("year",2026);
        two.putArray("tagIds").add(tb);long b=publish(two,other);
        ObjectNode three=base("shared");three.put("categoryId",child);three.put("abstract",marker);three.put("year",2025);
        three.putArray("tagIds").add(tc);publish(three,user);
        jdbc.update("UPDATE paper SET downloads=10 WHERE id=?",a);jdbc.update("UPDATE paper SET downloads=20 WHERE id=?",b);
        JsonNode page=search("q",marker,"categoryId",String.valueOf(root),"tagIds",ta+","+tb,"sort","downloads-desc","pageSize","1");
        assertEquals(2,page.path("total").asLong());assertEquals(b,page.path("list").get(0).path("id").asLong());
        JsonNode second=search("q",marker,"categoryId",String.valueOf(root),"tagIds",ta+","+tb,"sort","downloads-desc","pageSize","1","page","2");
        assertEquals(a,second.path("list").get(0).path("id").asLong());assertEquals(2,second.path("total").asLong());
        assertEquals(Collections.singleton(a),ids(search("q",marker,"categoryId",String.valueOf(root),"year","2024","journal",journal).path("list")));
        assertEquals(2,search("q",marker,"tagIds",ta+","+tb+","+ta).path("total").asLong());
        assertEquals(0,search("q",marker,"categoryId","9007199254740991").path("total").asLong());
    }
    long tag() throws Exception {
        return ok(api("POST","/tags",body("name",unique(),"color","#123456"),admin)).path("id").asLong();
    }
    @Test void sortingIsWhitelistedStableAndOutOfRangePagesKeepTheTotal() throws Exception {
        String marker=unique();List<Long> ids=new ArrayList<>();
        for(int year:new int[]{2024,2026,2025}) {
            ObjectNode p=base("shared");p.put("abstract",marker);p.put("year",year);ids.add(publish(p,user));
        }
        jdbc.update("UPDATE paper SET uploaded_at='2026-11-08 09:00:00',downloads=7 WHERE id IN (?,?,?)",ids.get(0),ids.get(1),ids.get(2));
        for(String sort:Arrays.asList("uploaded-desc","downloads-desc")) {
            JsonNode list=search("q",marker,"sort",sort).path("list");
            assertEquals(ids.get(2).longValue(),list.get(0).path("id").asLong());
            assertEquals(ids.get(1).longValue(),list.get(1).path("id").asLong());
        }
        assertEquals(ids.get(1).longValue(),search("q",marker,"sort","year-desc").path("list").get(0).path("id").asLong());
        JsonNode outside=search("q",marker,"page","2147483647","pageSize","100");
        assertEquals(3,outside.path("total").asLong());assertTrue(outside.path("list").isEmpty());
    }
    @Test void percentUnderscoreAndSqlFragmentsAreTreatedAsLiteralSearchText() throws Exception {
        String marker=unique();ObjectNode first=base("shared");first.put("title","Literal %_! "+marker);
        long id=publish(first,user);
        ObjectNode other=base("shared");other.put("title","Different literal "+unique());publish(other,user);
        assertEquals(Collections.singleton(id),ids(search("q","%_!").path("list")));
        assertEquals(0,search("q","' OR 1=1 --").path("total").asLong());
    }
    @Test void filterOptionsComeOnlyFromTheApprovedSharedPoolAndIncludeAncestorCounts() throws Exception {
        long root=ok(api("POST","/categories",body("name",unique(),"parentId",null),admin)).path("id").asLong();
        long child=ok(api("POST","/categories",body("name",unique(),"parentId",root),admin)).path("id").asLong();
        long privateCategory=ok(api("POST","/categories",body("name",unique(),"parentId",null),user)).path("id").asLong();
        long empty=ok(api("POST","/categories",body("name",unique(),"parentId",null),admin)).path("id").asLong();
        long usedTag=tag(),unusedTag=tag();String journal="Published journal "+unique(),secretJournal="Secret "+unique();
        ObjectNode p=base("shared");p.put("categoryId",child);p.put("journal",journal);p.putArray("tagIds").add(usedTag);publish(p,user);
        ObjectNode privateP=base("private");privateP.put("categoryId",privateCategory);privateP.put("journal",secretJournal);privateP.put("year",1901);
        create(privateP,user);
        ObjectNode pending=base("shared");pending.put("categoryId",empty);pending.putArray("tagIds").add(unusedTag);create(pending,other);
        JsonNode options=read("/shared-papers/filter-options",user);
        assertEquals(1,named(options.path("categories"),root).path("paperCount").asLong());
        assertEquals(1,named(options.path("categories"),child).path("paperCount").asLong());
        assertFalse(ids(options.path("categories")).contains(privateCategory));assertFalse(ids(options.path("categories")).contains(empty));
        assertTrue(ids(options.path("tags")).contains(usedTag));assertFalse(ids(options.path("tags")).contains(unusedTag));
        Set<String> journals=new HashSet<>();for(JsonNode value:options.path("journals"))journals.add(value.asText());
        assertTrue(journals.contains(journal));assertFalse(journals.contains(secretJournal));
        for(JsonNode year:options.path("years"))assertNotEquals(1901,year.asInt());
    }
    @Test void adminSharedListFiltersByNameDoiCategoryAndStateWithoutIncludingPrivatePapers() throws Exception {
        String marker=unique(),doi="10.9999/"+unique();ObjectNode p=base("shared");p.put("title",marker);p.put("doi",doi);
        long id=create(p,user);
        ObjectNode privateP=p.deepCopy().put("visibility","private").put("copyrightAgreed",false);create(privateP,user);
        JsonNode list=ok(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/papers")
                .header("Authorization",admin).param("title",marker).param("doi",doi).param("uploader","林")
                .param("categoryId","1").param("status","pending")));
        assertEquals(Collections.singleton(id),ids(list));
        JsonNode groups=read("/admin/review/papers",admin);
        assertEquals(3,groups.size());assertTrue(ids(groups.path("pending")).contains(id));
        ok(flow(id,"unshare",null,user));
        groups=read("/admin/review/papers",admin);
        for(String status:Arrays.asList("pending","approved","rejected"))assertFalse(ids(groups.path(status)).contains(id));
        assertEquals(Collections.singleton(id),ids(read("/admin/papers?title="+marker+"&status=offline",admin)));
    }
    @Test void malformedQueriesAndNonAdminHistoryReadsAreRejected() throws Exception {
        for(String[] input:new String[][]{{"page","0"},{"pageSize","101"},{"sort","id desc;drop table paper"},
                {"tagIds","1,foo"},{"tagIds","1,"},{"tagIds","9007199254740992"},{"categoryId","-1"},{"year","abc"}}) {
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request=
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/shared-papers").header("Authorization",user).param(input[0],input[1]);
            mvc.perform(request).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value(1005));
        }
        api("GET","/shared-papers",null,null).andExpect(status().isUnauthorized());
        api("GET","/admin/review/records?action=invalid",null,admin).andExpect(status().isUnprocessableEntity());
        api("GET","/admin/duplicates?status=invalid",null,admin).andExpect(status().isUnprocessableEntity());
        api("GET","/admin/duplicate-records",null,user).andExpect(status().isForbidden());
    }
}

