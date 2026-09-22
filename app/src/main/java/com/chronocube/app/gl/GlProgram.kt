package com.chronocube.app.gl

import android.opengl.GLES30

internal object GlProgram {
    fun create(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compile(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compile(GLES30.GL_FRAGMENT_SHADER, fragmentSource)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        check(linkStatus[0] == GLES30.GL_TRUE) {
            "OpenGL program link failed: " + GLES30.glGetProgramInfoLog(program)
        }
        return program
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        check(compileStatus[0] == GLES30.GL_TRUE) {
            val message = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            "OpenGL shader compile failed: " + message
        }
        return shader
    }
}
