package org.elixir_lang.annotator

import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * An [AnnotationHolder] that highlights nothing and passes each annotation created through it to [onCreate], with the
 * range last set on it. A silent annotation's message is empty. Every other call is a no-op, so an annotator that
 * registers a fix still runs to `create()`.
 */
internal fun recordingAnnotationHolder(onCreate: (TextRange?, String) -> Unit): AnnotationHolder =
    proxy(AnnotationHolder::class.java) { method, arguments ->
        if (method.returnType == AnnotationBuilder::class.java) {
            recordingBuilder(arguments?.getOrNull(1) as? String ?: "", onCreate)
        } else {
            defaultValue(method)
        }
    } as AnnotationHolder

private fun recordingBuilder(message: String, onCreate: (TextRange?, String) -> Unit): AnnotationBuilder {
    var range: TextRange? = null
    lateinit var builder: AnnotationBuilder

    // A fix builder's `registerFix()` returns to the annotation builder, so every chained interface leads back to it.
    fun link(type: Class<*>): Any = proxy(type) { method, arguments ->
        when {
            type == AnnotationBuilder::class.java && method.name == "range" -> {
                range = when (val target = arguments?.firstOrNull()) {
                    is TextRange -> target
                    is PsiElement -> target.textRange
                    is ASTNode -> target.textRange
                    else -> range
                }
                builder
            }
            type == AnnotationBuilder::class.java && method.name == "create" -> onCreate(range, message).let { null }
            method.returnType.isInstance(builder) -> builder
            method.returnType.isInterface -> link(method.returnType)
            else -> defaultValue(method)
        }
    }

    builder = link(AnnotationBuilder::class.java) as AnnotationBuilder

    return builder
}

private fun proxy(type: Class<*>, handle: (Method, Array<out Any?>?) -> Any?): Any =
    Proxy.newProxyInstance(AnnotationHolder::class.java.classLoader, arrayOf(type)) { self, method, arguments ->
        when (method.name.takeIf { method.declaringClass == Any::class.java }) {
            "equals" -> self === arguments?.getOrNull(0)
            "hashCode" -> System.identityHashCode(self)
            "toString" -> "recording ${type.simpleName}"
            else -> handle(method, arguments)
        }
    }

private fun defaultValue(method: Method): Any? = when (method.returnType) {
    java.lang.Boolean.TYPE -> false
    Integer.TYPE -> 0
    else -> null
}
