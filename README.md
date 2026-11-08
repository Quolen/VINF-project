# VINF AllMovie Crawler and Search Engine

This Java 17 Maven project crawls movie pages from AllMovie, stores the
downloaded HTML and extracted movie metadata, joins them with Wikipedia data,
and offers both a custom TF-IDF search engine and a Lucene search engine.

The crawler uses Java's built-in `HttpClient` and regular expressions. The
original crawler and search engine do not use Jsoup, DOM, XPath, Lucene, or SQL.

## Requirements

- Java 17
- Maven
- Internet access for crawling

## Spark preparation

The Maven project includes Apache Spark 3.5.9 for the Java-based data
processing phase. Maven downloads its dependencies; a separate Spark
installation is not required for the local test below. Use Java 17 for Spark.
The Wikipedia dump parser and the AllMovie–Wikipedia join are implemented.

On Windows, the local Spark file-output test needs Hadoop Windows helpers.
The current setup uses the third-party `winutils.exe` and `hadoop.dll` from
`cdarlint/winutils`, Hadoop 3.3.5, pinned to commit `7386986`. These are
unsigned community binaries, not official Apache releases. Spark's Maven
dependencies use Hadoop 3.3.4; the 3.3.5 helpers have been tested for local
file input and output but are not an exact version match.

- Set `HADOOP_HOME` to `C:\hadoop`.
- Add `C:\hadoop\bin` to the Windows user `Path`.
- Restart IntelliJ and terminals after changing environment variables.

Run the Spark environment test with:

```text
mvn -Dtest=SparkEnvironmentTest test
```

The test checks local file reading and writing, BZip2 input, and a small
in-memory join. A warning about native BZip2 is expected if Hadoop falls back
to its pure-Java codec.

Maven's test configuration supplies the Java 17 module option needed by the
test. When running Spark directly in IntelliJ, select a Java 17 JDK and add
`--add-opens=java.base/sun.nio.ch=ALL-UNNAMED` to the run configuration's VM
options.

The test Wikipedia dump is stored at
`data/enwiki-latest-pages-articles1.xml-p1p41242.bz2` and is ignored by Git.
The `latest` filename can point to a different dated dump in the future; the
download currently corresponds to the 2026-11-01 dump.

## Build and test

Compile the project:

```text
mvn package
```

Run the unit tests:

```text
mvn test
```

## Application modes

The application is started through `com.nikita.App`.

### Quick crawl: no arguments

```text
mvn exec:java -Dexec.mainClass=com.nikita.App
```

With no arguments, the crawler starts from the configured AllMovie seed URL
and downloads up to 5 pages. This mode is intended for quick testing.

### Massive crawl: `crawl`

```text
mvn exec:java -Dexec.mainClass=com.nikita.App -Dexec.args="crawl"
```

With the `crawl` argument, the crawler uses a limit of 15,000 pages. It logs
detailed crawler statistics every 100 successfully downloaded pages and again
when the crawl finishes.

The crawler keeps a 2,000 ms politeness delay between requests. A massive
crawl can therefore take many hours.

### Search: `search`

```text
mvn exec:java -Dexec.mainClass=com.nikita.App -Dexec.args="search"
```

Search mode does not contact AllMovie. Choose **1** for the custom index or
**2** for Lucene. The custom index reads `data/extracted.tsv` into memory;
Lucene opens the previously built `data/lucene_index`:

```text
Enter query (or 'exit'):
```

For the custom index each query is ranked with standard and smooth IDF.
Lucene uses its default BM25 scoring. Each displays up to ten results.
Enter `exit` to stop the CLI.

Search mode should be run after the crawler has generated
`data/extracted.tsv` or, for Lucene, after building the Lucene index.

### Build the Lucene index: `lucene-index`

```text
mvn exec:java -Dexec.mainClass=com.nikita.App -Dexec.args="lucene-index"
```

This reads the quoted, potentially multiline `data/joined_data.tsv` and
creates `data/lucene_index/`. It indexes one document per TSV record, including
unmatched movies with empty Wikipedia text. It refuses to overwrite an existing
index; choose a different index path in code or deliberately move the old
index aside before rebuilding. The existing TSV and crawler data are not
modified. Indexing uses Lucene 9.10.0 and Java, not PyLucene.

| Field | Type | Purpose |
|---|---|---|
| `url` | Stored `StringField` | Exact movie identifier displayed with results |
| `title` | Stored `TextField` | Searchable movie title |
| `synopsis` | Stored `TextField` | Searchable AllMovie description |
| `genres` | Stored `TextField` | Preserves searchable genre metadata |
| `wiki_text` | Stored `TextField` | Searchable Wikipedia content, possibly empty |

`StandardAnalyzer` tokenizes text for both indexing and query parsing.
Unqualified Lucene queries search `title`, `synopsis` and `wiki_text`; use
`genres:drama` for an explicit genre query. The query parser supports Boolean
queries such as `mafia AND family`, phrase queries such as `"space travel"`,
and fuzzy queries such as `matrix~1`. Scores from BM25 and the custom TF-IDF
methods are not directly comparable. Joined Wikipedia text is raw wikitext,
and title-only matches may refer to a different subject or film.

### Parse Wikipedia: `spark-parse`

In IntelliJ, run `com.nikita.App` with the program argument `spark-parse`,
Java 17, and the VM option `--add-opens=java.base/sun.nio.ch=ALL-UNNAMED`.
Alternatively, in PowerShell:

```powershell
$env:MAVEN_OPTS = "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
mvn exec:java "-Dexec.mainClass=com.nikita.App" "-Dexec.args=spark-parse"
```

The parser reads `data/enwiki-latest-pages-articles1.xml-p1p41242.bz2`,
assembles `<page>` elements across partition boundaries, extracts title and
text with regex, filters redirects and titles starting with selected Wikipedia
namespace prefixes (case-insensitive), and writes `title` and `text` columns to
`data/wiki_parquet`. The output is **overwritten** on each run. XML entities
remain encoded. The filter does not check the XML namespace ID, so namespaces
not in the configured prefix list can remain; ordinary titles containing
colons are preserved.

Eight is the requested minimum partition count, not a guarantee of eight
equally sized partitions: actual BZip2 splitting depends on the compressed
blocks and Spark's input reader. Page assembly requires an initial scan and a
shuffle, including to join fragments that cross partition boundaries.

### Join AllMovie and Wikipedia: `spark-join`

Run `com.nikita.App` with the program argument `spark-join`, using the same
Java 17 VM option and Hadoop configuration as `spark-parse`. In PowerShell:

```powershell
$env:MAVEN_OPTS = "-Xmx4g --add-opens=java.base/sun.nio.ch=ALL-UNNAMED"
mvn exec:java "-Dexec.mainClass=com.nikita.App" "-Dexec.args=spark-join"
```

The join reads `data/extracted.tsv` and `data/wiki_parquet`. It removes
parenthesized parts from AllMovie titles, trims and lowercases both join
keys, and uses an exact left join. Unmatched movies remain in the output.
Matching only by title can join an unrelated Wikipedia page or multiple
pages with the same normalized title; year and film identity are not checked.
The parser logs input movie count, matched input rows, distinct matched
Wikipedia titles, and prints three text snippets.
The join defaults to two local workers and smaller Parquet batches to reduce
memory pressure when reading long Wikipedia articles. Set the `spark.master`
Java system property to change the execution master.

The output is one UTF-8 file, `data/joined_data.tsv`, with a header, all 12
AllMovie fields, and `wiki_text`. Existing output is not overwritten. Spark
uses a single output partition, which can limit scalability. Fields containing
tabs, quotes, or newlines are quoted using double quotes; embedded quotes are
doubled. Wikipedia text can therefore span multiple physical lines. Read it
with a TSV reader supporting quoted multiline fields (Spark options `sep=\\t`,
`header=true`, `multiLine=true`, `escape="`, `maxCharsPerColumn=-1`; use
`.option("sep", "\t")` in Java).

## Output files

The crawler creates the following files and directories:

```text
data/raw_html/*.html
data/documents.tsv
data/queue.tsv
data/extracted.tsv
```

- `data/raw_html/` contains the raw HTML downloaded from AllMovie.
- `documents.tsv` contains the downloaded URL, HTML character count, and
  SHA-256 hash.
- `queue.tsv` contains discovered URLs, anchor text, and the `PENDING` status.
- `extracted.tsv` contains the extracted movie fields and token count.

The crawler reads `documents.tsv` and `queue.tsv` when it starts. Successfully
stored document URLs are restored as visited, and pending URLs are restored
to the frontier. This allows a later run to continue from previously stored
state instead of intentionally downloading successfully completed pages again.

## Configuration

The current runtime configuration is in `src/main/java/com/nikita/App.java`:

```java
new CrawlerEngine(
        2000,
        "data/raw_html",
        "data/documents.tsv",
        "data/queue.tsv",
        "data/extracted.tsv"
);
```

The values configure:

1. The politeness delay in milliseconds.
2. The raw HTML directory.
3. The downloaded-document metadata file.
4. The pending-URL queue file.
5. The extracted-movie file.

The seed URL and default crawl limits are also configured in `App.java`.
`HttpFetcher` uses the required User-Agent, Accept-Language header,
10-second connection timeout, and 15-second request timeout.

## Project structure

```text
src/main/java/com/nikita/
├── App.java
├── cli/QueryCli.java
├── crawler/
│   ├── engine/
│   ├── http/
│   ├── parser/
│   └── storage/
├── extractor/EntityExtractor.java
├── indexer/
└── model/
```

The crawler follows this flow:

```text
CrawlerEngine
  -> UrlFrontier
  -> HttpFetcher
  -> EntityExtractor and FileStorage
  -> LinkExtractor
  -> UrlFilter
  -> UrlFrontier
```

The search flow is:

```text
extracted.tsv
  -> Indexer
  -> Tokenizer
  -> QueryCli
  -> TF-IDF results
```
