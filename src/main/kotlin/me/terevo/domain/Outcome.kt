package me.terevo.domain

sealed interface Outcome<out T> {
    data class Ok<out T>(val value: T) : Outcome<T>
    data class Err(val error: DomainError) : Outcome<Nothing>
}

fun <T> T.asOk(): Outcome<T> = Outcome.Ok(this)

fun DomainError.asErr(): Outcome<Nothing> = Outcome.Err(this)

val Outcome<*>.isOk: Boolean get() = this is Outcome.Ok

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Ok -> Outcome.Ok(transform(value))
    is Outcome.Err -> this
}

inline fun <T, R> Outcome<T>.flatMap(transform: (T) -> Outcome<R>): Outcome<R> = when (this) {
    is Outcome.Ok -> transform(value)
    is Outcome.Err -> this
}

fun <T> Outcome<T>.getOrNull(): T? = when (this) {
    is Outcome.Ok -> value
    is Outcome.Err -> null
}

fun Outcome<*>.errorOrNull(): DomainError? = when (this) {
    is Outcome.Ok -> null
    is Outcome.Err -> error
}

inline fun <T> Outcome<T>.getOrElse(fallback: (DomainError) -> T): T = when (this) {
    is Outcome.Ok -> value
    is Outcome.Err -> fallback(error)
}
