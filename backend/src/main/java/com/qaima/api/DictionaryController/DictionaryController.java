package com.qaima.api.DictionaryController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.dictionary.DictionaryAliasDto;
import com.qaima.dto.dictionary.DictionaryInitialCountDto;
import com.qaima.dto.dictionary.DictionaryTermDto;
import com.qaima.service.dictionary.DictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dictionary")
public class DictionaryController {

    private final DictionaryService dictionaryService;

    @GetMapping
    public Mono<ApiResponse<List<DictionaryTermDto>>> search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "initial", required = false) String initial,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        return dictionaryService.search(q, initial, page, size)
                .map(ApiResponse::success);
    }

    @GetMapping("/autocomplete")
    public Mono<ApiResponse<List<String>>> autocomplete(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        return dictionaryService.autocomplete(q, size)
                .map(ApiResponse::success);
    }

    @GetMapping("/initials")
    public Mono<ApiResponse<List<DictionaryInitialCountDto>>> initials() {
        return dictionaryService.initialCounts()
                .map(ApiResponse::success);
    }

    @GetMapping("/{term}")
    public Mono<ApiResponse<DictionaryTermDto>> getOne(@PathVariable("term") String term) {
        return dictionaryService.getByTerm(term)
                .map(ApiResponse::success);
    }

    @GetMapping("/{term}/aliases")
    public Mono<ApiResponse<List<DictionaryAliasDto>>> aliases(@PathVariable("term") String term) {
        return dictionaryService.getAliases(term)
                .map(ApiResponse::success);
    }
}
