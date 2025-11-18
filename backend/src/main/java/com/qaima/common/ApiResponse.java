package com.qaima.common;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * QAIMA 공통 응답 클래스
 *
 * - 모든 API 응답은 이 형식을 따른다.
 * - data: 실제 응답 데이터
 * - meta: 요청 ID, 상태, 타임스탬프 등 메타 정보
 * - errors: 에러 목록 (성공 시 비어 있음)
 *
 * @param <T> 응답 데이터의 타입
 */
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

    /** 실패 응답 생성 */
    public static <T> ApiResponse<T> error(String code, String message) {
        Meta meta = Meta.failure();
        ApiError err = new ApiError(code, message);
        return new ApiResponse<>(meta, null, List.of(err));
    }

    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public List<ApiError> getErrors() { return errors; }
    public void setErrors(List<ApiError> errors) { this.errors = errors; }
}
