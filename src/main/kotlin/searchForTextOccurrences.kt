import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.useLines
import kotlin.streams.asSequence

interface Occurrence {
    val file: Path
    val line: Int
    val offset: Int
}

data class TextOccurrence(
    override val file: Path,
    override val line: Int,
    override val offset: Int
) : Occurrence

fun searchForTextOccurrences(
    stringToSearch: String,
    directory: Path
): Flow<Occurrence> {
    if (stringToSearch.isEmpty()) {
        return emptyFlow()
    }

    if (!directory.exists() || !directory.isDirectory()) {
        return emptyFlow()
    }

    val files = getAllRegularFiles(directory)

    return channelFlow {
        for (file in files) {
            launch(Dispatchers.IO) {
                try {
                    val occurrences = searchInFile(file, stringToSearch)
                    for (occ in occurrences) send(occ)
                } catch (_: Exception) {
                    // ignore unreadable files
                }
            }
        }
    }
}

private fun getAllRegularFiles(directory: Path): List<Path> = try {
    Files.walk(directory)
        .asSequence()
        .filter { it.isRegularFile() }
        .toList()
} catch (_: Exception) {
    emptyList()
}

private fun searchInFile(file: Path, searchString: String): List<Occurrence> {
    val occurrences = mutableListOf<Occurrence>()

    try {
        file.useLines { lines ->
            lines.forEachIndexed { lineIndex, lineContent ->
                var startIndex = 0
                while (true) {
                    val index = lineContent.indexOf(searchString, startIndex)
                    if (index == -1) break

                    occurrences.add(
                        TextOccurrence(
                            file = file,
                            line = lineIndex + 1, // 1-indexed line numbers
                            offset = index
                        )
                    )
                    startIndex = index + 1 // Find overlapping occurrences
                }
            }
        }
    } catch (e: Exception) {
    }

    return occurrences
}