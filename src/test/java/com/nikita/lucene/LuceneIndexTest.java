package com.nikita.lucene;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuceneIndexTest {
    private static final String HEADER =
            "url\ttitle\tyear\tdirector\tcast\tgenres\truntime\tsynopsis\tthemes\tmpaa\tcountry\ttokens\twiki_text\n";

    @Test
    void buildsAndSearchesMultilineTsv(@TempDir Path temp) throws Exception {
        Path input = temp.resolve("joined.tsv");
        String text = "  Villain\n\"quoted\" scene\tunobtainium  ";
        Files.writeString(input, HEADER
                + "u1\tHero Movie\t2000\t\t\tAction\t\trescue mission\t\t\t\t0\t"
                + "\"  Villain\n\"\"quoted\"\" scene\tunobtainium  \"\n"
                + "u2\tOther Movie\t2001\t\t\tDrama\t\tspace journey\t\t\t\t0\t\n",
                StandardCharsets.UTF_8);
        Path index = temp.resolve("index");
        assertEquals(2, new LuceneIndexer().build(input, index));
        assertThrows(IOException.class, () -> new LuceneIndexer().build(input, index));
        try (FSDirectory directory = FSDirectory.open(index);
             DirectoryReader reader = DirectoryReader.open(directory)) {
            assertEquals(2, reader.numDocs());
            assertEquals(text, reader.storedFields().document(0).get("wiki_text"));
            assertEquals("", reader.storedFields().document(1).get("wiki_text"));
        }
        try (LuceneSearcher searcher = new LuceneSearcher(index)) {
            assertEquals("u1", searcher.search("unobtainium").get(0).url());
            assertEquals("u1", searcher.search("\"rescue mission\"").get(0).url());
            assertEquals("u1", searcher.search("hero AND villain").get(0).url());
            assertEquals("u1", searcher.search("herp~1").get(0).url());
            assertEquals("u2", searcher.search("journey").get(0).url());
            assertTrue(searcher.search("  ").isEmpty());
            assertThrows(ParseException.class, () -> searcher.search("\"unterminated"));
        }
    }

    @Test
    void rejectsMalformedInputWithoutPublishingPartialIndex(@TempDir Path temp) throws IOException {
        Path input = temp.resolve("broken.tsv");
        Files.writeString(input, HEADER + "u1\tonly two columns\n", StandardCharsets.UTF_8);
        Path index = temp.resolve("index");
        assertThrows(IOException.class, () -> new LuceneIndexer().build(input, index));
        assertFalse(Files.exists(index));
    }

    @Test
    void limitsResultsToTen(@TempDir Path temp) throws Exception {
        Path input = temp.resolve("many.tsv");
        StringBuilder rows = new StringBuilder(HEADER);
        for (int i = 0; i < 12; i++) {
            rows.append("u").append(i)
                    .append("\tcommon title\t\t\t\t\t\t\t\t\t\t0\t\n");
        }
        Files.writeString(input, rows, StandardCharsets.UTF_8);
        Path index = temp.resolve("index");
        assertEquals(12, new LuceneIndexer().build(input, index));
        try (LuceneSearcher searcher = new LuceneSearcher(index)) {
            assertEquals(10, searcher.search("common").size());
        }
    }
}
