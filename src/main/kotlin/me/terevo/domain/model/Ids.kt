package me.terevo.domain.model

import java.util.UUID

@JvmInline
value class PersonId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun next(): PersonId = PersonId(UUID.randomUUID())

        fun parse(text: String): PersonId = PersonId(UUID.fromString(text))
    }
}

@JvmInline
value class RelationId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun next(): RelationId = RelationId(UUID.randomUUID())

        fun parse(text: String): RelationId = RelationId(UUID.fromString(text))
    }
}

@JvmInline
value class MediaId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun next(): MediaId = MediaId(UUID.randomUUID())

        fun parse(text: String): MediaId = MediaId(UUID.fromString(text))
    }
}
