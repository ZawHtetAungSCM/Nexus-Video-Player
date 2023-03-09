package com.example.videoplayer.data.dto

sealed class Result<out R> {

    data class Success<out T>(val data: T): Result<T>() {
        override fun toString() = "Success [data=$data]"
    }

    data class Error(val message: String = ""): Result<Nothing>() {
        override fun toString() = message
    }

    data class Fail(val message: String): Result<Nothing>() {
        override fun toString() = message
    }
}