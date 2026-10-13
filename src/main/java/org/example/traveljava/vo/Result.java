package org.example.traveljava.vo;

import lombok.Data;

@Data
public class Result<T> { // 泛型类
    private Boolean success;
    private Integer code;
    private String message;
    private T data;
    private String error;
    private String rawResponse;

//    public Boolean getSuccess() {
//        return success;
//    }
//
//    public void setSuccess(boolean success) {
//        this.success = success;
//    }

    public static <T> Result<T> success(){
        Result<T> result = new Result<T>();
        result.setSuccess(true);
        result.setCode(200);
        return result;
    }

    public static <T> Result<T> success(T msg){
        Result<T> result = new Result<T>();
        result.setSuccess(true);
        result.setCode(200);
        result.setMessage("success");
        result.setData(msg);
        return result;
    }

    public static <T> Result<T> fail() {
        Result<T> result = new Result<T>();
        result.setSuccess(false);
        result.setCode(500);
        result.setMessage("fail");
        return result;
    }

    public static <T> Result<T> fail(Integer code,T msg) {
        Result<T> result = new Result<T>();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage("fail");
        result.setData(msg);
        return result;
    }

    public static <T> Result<T> error(String error,String rawResponse) {
        Result<T> result = new Result<T>();
        result.setSuccess(false);
        result.setError(error);
        result.setRawResponse(rawResponse);
        return result;
    }
}
