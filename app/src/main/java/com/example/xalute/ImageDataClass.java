package com.example.xalute;
public class ImageDataClass {
    private String imageBase64;
    private String name;
    private String birthDate;
    private String createdTime;
    private String createdDate;
    private String format;

    // 기본 생성자
    public ImageDataClass() {
    }

    // 모든 필드를 포함한 생성자
    public ImageDataClass(String imageBase64, String name, String birthDate, String createdTime, String createdDate, String format) {
        this.imageBase64 = imageBase64;
        this.name = name;
        this.birthDate = birthDate;
        this.createdTime = createdTime;
        this.createdDate = createdDate;
        this.format = format;
    }

    // Getter 메소드
    public String getImageBase64() {
        return imageBase64;
    }

    public String getName() {
        return name;
    }

    public String getBirthDate() {
        return birthDate;
    }

    public String getCreatedTime() {
        return createdTime;
    }

    public String getCreatedDate() {
        return createdDate;
    }

    public String getFormat() {
        return format;
    }

    // Setter 메소드
    public  void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public  void setName(String name) {
        this.name = name;
    }

    public  void setBirthDate(String birthDate) {
        this.birthDate = birthDate;
    }

    public  void setCreatedTime(String createdTime) {
        this.createdTime = createdTime;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
    }

    public  void setFormat(String format) {
        this.format = format;
    }
}
