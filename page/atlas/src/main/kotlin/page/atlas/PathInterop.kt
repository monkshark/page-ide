package page.atlas

import java.nio.file.Path
import page.shared.path.FilePath

fun Path.toFilePath(): FilePath = FilePath.of(toString())

fun FilePath.toNioPath(): Path = Path.of(value)
