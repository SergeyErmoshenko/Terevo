package me.terevo.gedcom

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome
import me.terevo.domain.model.FamilyTree

object GedcomFiles {
    fun preview(path: String): Outcome<GedcomPreview> = try {
        GedcomCodec.parse(decode(Files.readAllBytes(Path.of(path))))
    } catch (failure: java.io.IOException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    }

    fun export(tree: FamilyTree, path: String): Outcome<Unit> = try {
        Files.writeString(Path.of(path), GedcomCodec.export(tree), StandardCharsets.UTF_8)
        Outcome.Ok(Unit)
    } catch (failure: java.io.IOException) {
        Outcome.Err(DomainError.Storage.Failure(failure.message.orEmpty()))
    }

    private fun decode(bytes: ByteArray): String = when {
        bytes.startsWith(UTF8_BOM) -> bytes.copyOfRange(UTF8_BOM.size, bytes.size).toString(StandardCharsets.UTF_8)
        bytes.startsWith(UTF16_LE_BOM) -> bytes.copyOfRange(UTF16_LE_BOM.size, bytes.size).toString(StandardCharsets.UTF_16LE)
        bytes.startsWith(UTF16_BE_BOM) -> bytes.copyOfRange(UTF16_BE_BOM.size, bytes.size).toString(StandardCharsets.UTF_16BE)
        else -> bytes.toString(StandardCharsets.UTF_8)
    }
}

private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
    size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())
private val UTF16_LE_BOM = byteArrayOf(0xFF.toByte(), 0xFE.toByte())
private val UTF16_BE_BOM = byteArrayOf(0xFE.toByte(), 0xFF.toByte())
