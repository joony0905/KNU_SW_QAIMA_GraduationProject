// backend/src/main/java/com/qaima/common/ApiResponse.java
package com.qaima.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Collections;
import java.util.List;

/**
 * QAIMA 공통 응답 클래스
 *
 * - data: 실제 응답 데이터
 * - meta: 요청 ID, 상태, 타임스탬프, warnings
 * - errors: 에러 목록 (성공 시 비어 있음)
 */
@JsonIgnoreProperties({"success"})
public class ApiResponse<T> {

    private Meta meta;
    private T data;
    private List<ApiError> errors;

    public ApiResponse() {}

    private ApiResponse(Meta meta, T data, List<ApiError> errors) {
        this.meta = meta;
        this.data = data;
        this.errors = errors;
    }

    /** 성공 응답 생성 */
    public static <T> ApiResponse<T> success(T data) {
        Meta meta = Meta.success();
        return new ApiResponse<>(meta, data, Collections.emptyList());
    }

    /**
     * 성공(부분 성공 포함) + warning 1개 추가 (레거시 호환 포함)
     * - 신규 정책: meta.warnings에 누적
     */
    public static <T> ApiResponse<T> successWithWarning(T data, String warning) {
        Meta meta = Meta.success();
        meta.addWarning(warning);
        return new ApiResponse<>(meta, data, Collections.emptyList());
    }

    /**
     * 성공(부분 성공 포함) + warnings 여러 개 추가 (신규)
     */
    public static <T> ApiResponse<T> successWithWarnings(T data, List<String> warnings) {
        Meta meta = Meta.success();
        if (warnings != null) {
            for (String w : warnings) meta.addWarning(w);
        }
        return new ApiResponse<>(meta, data, Collections.emptyList());
    }

    /** 실패 응답 생성 */
    public static <T> ApiResponse<T> error(String code, String message) {
        Meta meta = Meta.failure();
        ApiError err = new ApiError(code, message);
        return new ApiResponse<>(meta, null, List.of(err));
    }

    public static <T> ApiResponse<T> internalError(String code, String message) {
        ApiError error = new ApiError(code, message);
        return new ApiResponse<>(Meta.failure(), null, List.of(error));
    }

    /**
     * Transitional note:
     * - `success` was exposed as a derived boolean via bean getter.
     * - Official envelope contract is `meta/data/errors`; hide derived field from JSON.
     */
    @JsonIgnore
    public boolean isSuccess() {
        return meta != null && "success".equalsIgnoreCase(meta.getStatus());
    }

    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public List<ApiError> getErrors() { return errors; }
    public void setErrors(List<ApiError> errors) { this.errors = errors; }
}
