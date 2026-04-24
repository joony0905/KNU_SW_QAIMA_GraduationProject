package com.qaima.api.DictionaryAdminController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.dictionary.DictionaryAliasDto;
import com.qaima.dto.dictionary.DictionaryAliasUpsertRequestDto;
import com.qaima.dto.dictionary.DictionaryTermDto;
import com.qaima.dto.dictionary.DictionaryUpsertRequestDto;
import com.qaima.service.dictionary.DictionaryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/dictionary")
public class DictionaryAdminController {

    private final DictionaryService dictionaryService;

    @PutMapping
    public Mono<ApiResponse<DictionaryTermDto>> upsert(@Valid @RequestBody DictionaryUpsertRequestDto dto) {
        return dictionaryService.upsert(dto)
                .map(ApiResponse::success);
    }

    @PutMapping("/aliases")
    public Mono<ApiResponse<DictionaryAliasDto>> upsertAlias(@Valid @RequestBody DictionaryAliasUpsertRequestDto dto) {
        return dictionaryService.upsertAlias(dto)
                .map(ApiResponse::success);
    }

    @DeleteMapping("/{term}")
    public Mono<ApiResponse<Void>> delete(@PathVariable("term") String term) {
        return dictionaryService.delete(term)
                .thenReturn(ApiResponse.success(null));
    }

    @DeleteMapping("/aliases")
    public Mono<ApiResponse<Void>> deleteAlias(@RequestParam("alias") String alias) {
        return dictionaryService.deleteAlias(alias)
                .thenReturn(ApiResponse.success(null));
    }
}
