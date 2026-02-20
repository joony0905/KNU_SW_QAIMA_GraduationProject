package com.qaima.api.DictionaryController;

import com.qaima.common.ApiResponse;
import com.qaima.dto.DictionaryInitialCountDto;
import com.qaima.dto.DictionaryTermDto;
import com.qaima.service.DictionaryService;
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

    /**
     * 용어를 검색/목록 조회합니다.
     * 예시:
     * - GET /api/v1/dictionary?q=PER
     * - GET /api/v1/dictionary?initial=ㄱ&page=0&size=50
     */
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

    /**
     * 접두어 기준 자동완성 조회입니다.
     * 예시: GET /api/v1/dictionary/autocomplete?q=PER&size=10
     */
    @GetMapping("/autocomplete")
    public Mono<ApiResponse<List<String>>> autocomplete(
            @RequestParam(name = "q") String q,
            @RequestParam(name = "size", required = false) Integer size
    ) {
        return dictionaryService.autocomplete(q, size)
                .map(ApiResponse::success);
    }

    /**
     * ㄱ~ㅎ / A~Z 버튼용 인덱스 데이터를 조회합니다.
     * DB에 존재하는 initial만 initial -> count 요약으로 반환합니다.
     */
    @GetMapping("/initials")
    public Mono<ApiResponse<List<DictionaryInitialCountDto>>> initials() {
        return dictionaryService.initialCounts()
                .map(ApiResponse::success);
    }

    /**
     * 용어 상세 조회입니다.
     * 예시: GET /api/v1/dictionary/PER
     */
    @GetMapping("/{term}")
    public Mono<ApiResponse<DictionaryTermDto>> getOne(@PathVariable("term") String term) {
        return dictionaryService.getByTerm(term)
                .map(ApiResponse::success);
    }
}
