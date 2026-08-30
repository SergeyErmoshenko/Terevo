package me.terevo.testing

import kotlin.test.fail
import me.terevo.domain.DomainError
import me.terevo.domain.Outcome

fun <T> Outcome<T>.shouldBeOk(): T = when (this) {
    is Outcome.Ok -> value
    is Outcome.Err -> fail("expected Ok but was Err($error)")
}

fun Outcome<*>.shouldBeErr(): DomainError = when (this) {
    is Outcome.Ok -> fail("expected Err but was Ok($value)")
    is Outcome.Err -> error
}
