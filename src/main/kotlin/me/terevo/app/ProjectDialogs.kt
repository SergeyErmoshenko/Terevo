package me.terevo.app

import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.nio.file.Files
import me.terevo.domain.port.ProjectLocation

class ProjectDialogs(
    private val owner: Frame?,
    private val directories: ProjectDirectories = ProjectDirectories(),
) {
    fun chooseCreate(): ProjectLocation? {
        Files.createDirectories(directories.root)
        val selected = choose(FileDialog.SAVE, "Создать проект", directories.root.toFile()) ?: return null
        val name = selected.fileName.removeSuffix(".${ProjectLocation.EXTENSION}")
        return directories.location(name)
    }

    fun chooseOpen(): ProjectLocation? = choose(FileDialog.LOAD, "Открыть проект")

    fun chooseMedia(): String? = chooseFile(FileDialog.LOAD, "Добавить фото или документ")?.absolutePath

    fun chooseGedcomImport(): String? = chooseFile(FileDialog.LOAD, "Импорт GEDCOM", pattern = "*.ged")?.absolutePath

    fun chooseGedcomExport(): String? = chooseFile(FileDialog.SAVE, "Экспорт GEDCOM", pattern = "*.ged")
        ?.let { if (it.extension.equals("ged", true)) it else File("${it.absolutePath}.ged") }
        ?.absolutePath

    fun choosePngExport(): String? = chooseFile(FileDialog.SAVE, "Экспорт в PNG", pattern = "*.png")
        ?.let { if (it.extension.equals("png", true)) it else File("${it.absolutePath}.png") }
        ?.absolutePath

    fun choosePdfExport(): String? = chooseFile(FileDialog.SAVE, "Экспорт в PDF", pattern = "*.pdf")
        ?.let { if (it.extension.equals("pdf", true)) it else File("${it.absolutePath}.pdf") }
        ?.absolutePath

    private fun choose(mode: Int, title: String, directory: File? = null): ProjectLocation? {
        val dialog = FileDialog(owner, title, mode)
        dialog.file = "*.${ProjectLocation.EXTENSION}"
        dialog.directory = directory?.absolutePath
        dialog.isVisible = true
        val fileName = dialog.file ?: return null
        return ProjectLocation(File(dialog.directory, fileName).absolutePath)
    }

    private fun chooseFile(mode: Int, title: String, directory: File? = null, pattern: String? = null): File? {
        val dialog = FileDialog(owner, title, mode)
        dialog.directory = directory?.absolutePath
        dialog.file = pattern
        dialog.isVisible = true
        val fileName = dialog.file ?: return null
        return File(dialog.directory, fileName)
    }
}
