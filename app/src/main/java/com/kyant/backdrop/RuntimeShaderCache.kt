package com.kyant.backdrop

import android.os.Build
import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            error("RuntimeShader requires API 33+")
        }
        return runtimeShaders.getOrPut(key) { RuntimeShader(string) }
    }

    fun clear() {
        runtimeShaders.clear()
    }
}
