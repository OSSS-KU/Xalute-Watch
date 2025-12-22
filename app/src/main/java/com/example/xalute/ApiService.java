package com.example.xalute;

import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Part;
import okhttp3.MultipartBody;
import retrofit2.http.Multipart;

public interface ApiService {
    @POST("/mutation/addImageData")
    Call<ResponseBody> addNewImage(@Body ImageDataClass imageData);

    @Multipart
    @POST("predict1") // 서버의 엔드포인트
    Call<ResponseBody> uploadEcgFile(
            @Part MultipartBody.Part file,
            @Part("description") RequestBody description
    );
}
