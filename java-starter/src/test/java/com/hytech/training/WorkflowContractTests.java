package com.hytech.training;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.fail;

class WorkflowContractTests {
    @Disabled("Day 2：完成情境後移除 Disabled，並以 expected-results.json 驗證狀態與外部操作次數。")
    @ParameterizedTest
    @ValueSource(strings = {"P0-01", "P0-02", "P0-03", "P0-04", "P0-05", "P0-06"})
    void P0情境符合共用預期結果(String scenarioId) {
        fail("請完成 " + scenarioId + " 的 Arrange／Act／Assert，再解除 Disabled。");
    }
}
