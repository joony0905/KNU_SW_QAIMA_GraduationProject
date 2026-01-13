package com.qaima.common.exception;

/**
 * 오타 호환용 예외
 * 신규 코드는 {@link ResourceNotFoundException} 사용 권장.
 */

@Deprecated
public class ResourseNotFoundException extends ResourceNotFoundException {
    public ResourseNotFoundException(String message) {
        super(message);
    }
}
