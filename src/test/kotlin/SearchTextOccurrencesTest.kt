import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.*

@Suppress("UNCHECKED_CAST")
private fun invokeSearchInFile(file: Path, query: String): List<Occurrence> {
    val clazz = Class.forName("SearchForTextOccurrencesKt")
    val method = clazz.getDeclaredMethod("searchInFile", Path::class.java, String::class.java)
    method.isAccessible = true
    return method.invoke(null, file, query) as List<Occurrence>
}

@Suppress("UNCHECKED_CAST")
private fun invokeGetAllRegularFiles(dir: Path): List<Path> {
    val clazz = Class.forName("SearchForTextOccurrencesKt")
    val method = clazz.getDeclaredMethod("getAllRegularFiles", Path::class.java)
    method.isAccessible = true
    return method.invoke(null, dir) as List<Path>
}

class SearchForTextOccurrencesTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setup() {
        tempDir = Files.createTempDirectory("search_test")
    }

    @AfterTest
    fun cleanup() {
        tempDir.toFile().deleteRecursively()
    }

    @Test
    fun `test basic single occurrence`() {
        val file = tempDir.resolve("test.txt")
        file.writeText("Hello World")

        val results = invokeSearchInFile(file, "World")

        assertEquals(1, results.size)
        assertEquals(file, results[0].file)
        assertEquals(1, results[0].line)
        assertEquals(6, results[0].offset)
    }

    @Test
    fun `test multiple occurrences on same line`() {
        val file = tempDir.resolve("test.txt")
        file.writeText("foo bar foo baz foo")

        val results = invokeSearchInFile(file, "foo")

        assertEquals(3, results.size)
        assertEquals(0, results[0].offset)
        assertEquals(8, results[1].offset)
        assertEquals(16, results[2].offset)
    }

    @Test
    fun `test multiple occurrences across lines`() {
        val file = tempDir.resolve("test.txt")
        file.writeText("line1 test\nline2 test\nline3")

        val results = invokeSearchInFile(file, "test")

        assertEquals(2, results.size)
        assertEquals(1, results[0].line)
        assertEquals(6, results[0].offset)
        assertEquals(2, results[1].line)
        assertEquals(6, results[1].offset)
    }

    @Test
    fun `test overlapping occurrences`() {
        val file = tempDir.resolve("test.txt")
        file.writeText("aaa")

        val results = invokeSearchInFile(file, "aa")

        // Should find "aa" at positions 0 and 1
        assertEquals(2, results.size)
        assertEquals(0, results[0].offset)
        assertEquals(1, results[1].offset)
    }

    @Test
    fun `test empty search string returns empty flow`() = runBlocking {
        val file = tempDir.resolve("test.txt")
        file.writeText("some content")

        val results = searchForTextOccurrences("", tempDir).toList()

        assertEquals(0, results.size)
    }

    @Test
    fun `test non-existent directory returns empty flow`() = runBlocking {
        val nonExistent = tempDir.resolve("does-not-exist")

        val results = searchForTextOccurrences("test", nonExistent).toList()

        assertEquals(0, results.size)
    }

    @Test
    fun `test empty file`() {
        val file = tempDir.resolve("empty.txt")
        file.writeText("")

        val results = invokeSearchInFile(file, "test")

        assertEquals(0, results.size)
    }

    @Test
    fun `test no matches`() {
        val file = tempDir.resolve("test.txt")
        file.writeText("Hello World")

        val results = invokeSearchInFile(file, "xyz")

        assertEquals(0, results.size)
    }

    @Test
    fun `test multiple files (integration)`() = runBlocking {
        val file1 = tempDir.resolve("file1.txt")
        val file2 = tempDir.resolve("file2.txt")
        file1.writeText("test in file 1")
        file2.writeText("test in file 2")

        val results = searchForTextOccurrences("test", tempDir).toList()

        assertEquals(2, results.size)
        assertTrue(results.any { it.file == file1 })
        assertTrue(results.any { it.file == file2 })
    }

    @Test
    fun `test getAllRegularFiles finds nested files`() {
        val subDir = tempDir.resolve("subdir")
        Files.createDirectory(subDir)
        val nested = subDir.resolve("nested.txt")
        nested.writeText("test content")
        val top = tempDir.resolve("top.txt")
        top.writeText("abc")

        val files = invokeGetAllRegularFiles(tempDir)

        assertTrue(files.contains(nested))
        assertTrue(files.contains(top))
    }

    @Test
    fun `test case sensitivity`() {
        val file = tempDir.resolve("test.txt")
        file.writeText("Test TEST test")

        val results = invokeSearchInFile(file, "test")

        // Should only match lowercase "test"
        assertEquals(1, results.size)
        assertEquals(10, results[0].offset)
    }

    @Test
    fun `test special characters`() {
        val file = tempDir.resolve("test.txt")
        file.writeText("Hello @#$ World")

        val results = invokeSearchInFile(file, "@#$")

        assertEquals(1, results.size)
        assertEquals(6, results[0].offset)
    }

    @Test
    fun `test concurrent processing with many files (integration)`() = runBlocking {
        // Create many files to test concurrent processing
        repeat(50) { i ->
            val file = tempDir.resolve("file$i.txt")
            file.writeText("target found in file $i")
        }

        val results = searchForTextOccurrences("target", tempDir).toList()

        assertEquals(50, results.size)
        // Verify all files were processed
        assertEquals(50, results.map { it.file }.distinct().size)
    }

    @Test
    fun `test unicode characters`() {
        val file = tempDir.resolve("test.txt")
        file.writeText("Hello 世界 World")

        val results = invokeSearchInFile(file, "世界")

        assertEquals(1, results.size)
        assertEquals(6, results[0].offset)
    }
}