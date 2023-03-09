package com.example.videoplayer

import com.example.videoplayer.service.RetrofitService
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response
import com.example.videoplayer.data.dto.Result
import com.example.videoplayer.data.dto.ReturnData

class MainRepository(private val retrofitService: RetrofitService) {
    // Attributes
    companion object {
        //
    }

    // Token
    private var _token: String? = null
    val token get() = _token!!

    // User ID
    private var _userId: String? = null
    val userId get() = _userId!!

    // User Name
    private var _userName: String? = null
    val userName get() = _userName!!

//    init {
//        fetchUser()
//    }
//
//    fun saveUser(user: User) {
//        preferenceService.setUser(user)
//        fetchUser()
//    }
//
//    fun clearUser() {
//        preferenceService.clear()
//        fetchUser()
//    }
//
//    private fun fetchUser() {
//        preferenceService.apply {
//            _token = getToken()
//            _userId = getUserId()
//            _userName = getUserName()
//            _userType = getUserType()
//            _ferryToken = getFerryToken()
//            _driverName = getDriverName()
//        }
//    }

    suspend fun getVideoList() = mapToResult(retrofitService.getVideoList())
//    suspend fun saveTimeList(jsonObject: JsonObject) =
//        mapToResult(retrofitService.saveTimeList(token, jsonObject))



    private suspend fun <T> mapToResult(response: Response<T>): Result<T?> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                when (response.code()) {
                    200 -> Result.Success(response.body())
                    202 -> {
                        val responseBody = response.body()
                        if (responseBody is ReturnData) {
                            Result.Error(responseBody.responseDescription)
                        } else {
                            val returnDataJson = JSONObject(responseBody.toString())
                            val description = returnDataJson.getString("responseDescription")
                            Result.Error(description)
                        }
                    }
                    400 -> {
                        Result.Error(
                            JSONObject(
                                response.errorBody()!!.string()
                            ).getString("responseDescription")
                        )
                    }
                    401 -> {
//                        clearUser()
//                        preferenceService.gotoLogin()
                        Result.Error("Your token is expired")
                    }
                    201 -> Result.Success(response.body())
                    else -> Result.Error("Error Code ${response.code()}")
                }
            } catch (e: Exception) {
                Result.Fail("Something went Wrong")
            }
        }
}