package com.qaima.api.DictionaryAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.DictionaryTermDto;
import com.qaima.dto.DictionaryUpsertRequestDto;
import com.qaima.service.DictionaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dictionary")
public class DictionaryAdminController {

    private final DictionaryService dictionaryService;

    /**
     * 용어를 생성하거나 수정합니다.
     */
    @PutMapping
    public Mono<ApiResponse<DictionaryTermDto>> upsert(@Valid @RequestBody DictionaryUpsertRequestDto dto) {
        return dictionaryService.upsert(dto)
                .map(ApiResponse::success);
    }

    /**
     * 용어를 삭제합니다.
     */
    @DeleteMapping("/{term}")
    public Mono<ApiResponse<Void>> delete(@PathVariable("term") String term) {
        return dictionaryService.delete(term)
                .thenReturn(ApiResponse.success(null));
    }
}
