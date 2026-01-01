package com.qaima.api.FinancialAdminController;

import com.qaima.dto.FinancialDto;
import com.qaima.service.FinancialAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class FinancialAdminController {

    private final FinancialAdminService financialCommandService;
    
    @PostMapping("/stocks/{stockCode}/financials")
    public FinancialDto createFinancial(
            @PathVariable String stockCode,
            @RequestBody FinancialDto dto
    ) {
        return financialCommandService.create(stockCode, dto);
    }

    @PutMapping("/financials/{id}")
    public FinancialDto updateFinancial(
            @PathVariable Long id,
            @RequestBody FinancialDto dto
    ) {
        return financialCommandService.update(id, dto);
    }

    @DeleteMapping("/financials/{id}")
    public void deleteFinancial(@PathVariable Long id) {
        financialCommandService.delete(id);
    }
}
