package com.example.xalute;
import android.content.ContentUris;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Button;

import androidx.fragment.app.FragmentActivity;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import android.util.Log;
import android.util.Base64;
import java.util.Calendar;
import java.text.SimpleDateFormat;
import java.util.Locale;
import android.content.SharedPreferences;
import android.widget.Toast;

import com.example.xalute.R;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ImageSender extends FragmentActivity {
    private List<Uri> imageUris;
    private int currentIndex = 0;
    private ImageView imageView;
    private ImageButton buttonLeft, buttonRight;
    private Button buttonSend;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_shower);

        imageView = findViewById(R.id.imageView);
        buttonLeft = findViewById(R.id.buttonLeft);
        buttonRight = findViewById(R.id.buttonRight);
        buttonSend = findViewById(R.id.buttonSend);
        buttonLeft.setColorFilter(Color.parseColor("#FFFFFF"));
        buttonRight.setColorFilter(Color.parseColor("#FFFFFF"));


        imageUris = getAllImages();
        displayImage(currentIndex);

        buttonRight.setOnClickListener(v -> {
            if (currentIndex < imageUris.size() - 1) {
                currentIndex++;
                displayImage(currentIndex);
            }
        });

        buttonLeft.setOnClickListener(v -> {
            if (currentIndex > 0) {
                currentIndex--;
                displayImage(currentIndex);
            }
        });

        buttonSend.setOnClickListener(v -> {
            if (!imageUris.isEmpty()) {
                sendImage(imageUris.get(currentIndex));
            }
        });
    }


    private List<Uri> getAllImages() {
        List<Uri> images = new ArrayList<>();
        // 외부 저장소 URI
        Uri externalCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        // 내부 저장소 URI
        Uri internalCollection = MediaStore.Images.Media.INTERNAL_CONTENT_URI;

        // 가져올 컬럼 목록
        String[] projection = new String[] {
                MediaStore.Images.Media._ID
        };

        // 외부 저장소 이미지 불러오기
        List<Uri> externalImages = queryImages(externalCollection, projection);
        images.addAll(externalImages);
        Log.d("ImageCountLogger", "Number of images in external storage: " + externalImages.size());

        // 내부 저장소 이미지 불러오기
        List<Uri> internalImages = queryImages(internalCollection, projection);
        images.addAll(internalImages);
        Log.d("ImageCountLogger", "Number of images in internal storage: " + internalImages.size());

        return images;
    }

    private List<Uri> queryImages(Uri collectionUri, String[] projection) {
        List<Uri> imageList = new ArrayList<>();
        try (Cursor cursor = getContentResolver().query(collectionUri, projection, null, null, null)) {
            int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
            while (cursor.moveToNext()) {
                long id = cursor.getLong(idColumn);
                Uri imageUri = ContentUris.withAppendedId(collectionUri, id);
                imageList.add(imageUri);
            }
        } catch (Exception e) {
            Log.e("ImageLoader", "Failed to load images from " + collectionUri.toString(), e);
        }
        return imageList;
    }




    private void displayImage(int index) {
        Uri imageUri = imageUris.get(index);
        imageView.setImageURI(imageUri);
    }

    // 이미지 데이터를 바이트 배열로 변환하는 메소드
    private byte[] getBytesFromUri(Uri uri) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            if (inputStream == null) return null;

            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                byteArrayOutputStream.write(buf, 0, len);
            }
            return byteArrayOutputStream.toByteArray();
        } catch (IOException e) {
            Log.e("ImageSender", "Error reading image data", e);
            return null;
        }
    }

    // 이미지 URI를 사용하여 이미지 데이터를 서버에 전송하는 메소드
    private void sendImage(Uri imageUri) {
        // 현재 날짜와 시간을 가져오기 위한 Calendar 인스턴스 생성
        Calendar calendar = Calendar.getInstance();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentDate = dateFormat.format(calendar.getTime());
        String currentTime = timeFormat.format(calendar.getTime());
        SharedPreferences sharedPreferences = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        byte[] imageData = getBytesFromUri(imageUri);
        if (imageData != null) {
            String base64Image = Base64.encodeToString(imageData, Base64.DEFAULT);
            ImageDataClass imageDataClass = new ImageDataClass();
            imageDataClass.setImageBase64(base64Image);
            imageDataClass.setName(sharedPreferences.getString("name", ""));
            imageDataClass.setBirthDate(sharedPreferences.getString("birthDate", ""));
            imageDataClass.setCreatedTime(currentTime);
            imageDataClass.setCreatedDate(currentDate);
// 이미지 포맷 결정
            String mimeType = getContentResolver().getType(imageUri);
            String format = "unknown";
            if (mimeType != null) {
                if (mimeType.equals("image/jpeg")) {
                    format = "jpg";
                } else if (mimeType.equals("image/png")) {
                    format = "png";
                }
            }
            imageDataClass.setFormat(format);            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("http://api.xalute.org:3000")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            ApiService service = retrofit.create(ApiService.class);

            Call<ResponseBody> call = service.addNewImage(imageDataClass);
            call.enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        try {
                            String responseBodyString = response.body().string();
                            Log.d("Response", responseBodyString);
                            Toast.makeText(ImageSender.this, "Image successfully sent!", Toast.LENGTH_SHORT).show();

                        } catch (IOException e) {
                            Log.e("ResponseError", "응답 본문을 읽는 데 실패했습니다.", e);
                        }
                    } else {
                        Log.d("ResponseError", "통신 실패: " + response.code());
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> call, Throwable t) {
                    Log.d("success", "통신 실패");
                }
            });
        }
    }
}

