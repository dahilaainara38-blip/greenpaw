package com.greenpaw.ai;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.greenpaw.agent.repository.ActionConfirmationRepository;
import com.greenpaw.agent.repository.ToolTraceRepository;
import com.greenpaw.agent.tools.ImageAnalysisTool;
import com.greenpaw.agent.tools.ImageEditTool;
import com.greenpaw.agent.tools.ImageGenerationTool;
import com.greenpaw.agent.tools.WebSearchTool;
import com.greenpaw.agent.service.ArtifactService;
import com.greenpaw.care.service.CareAdvancedService;
import com.greenpaw.care.service.CareRecordService;
import com.greenpaw.care.service.CareReminderService;
import com.greenpaw.care.service.NearbyServiceSearchService;
import com.greenpaw.care.service.PetCareQueryService;
import com.greenpaw.care.service.PetFoodSafetyService;
import com.greenpaw.care.service.PlantSafetyQueryService;
import com.greenpaw.chat.UserSessionService;
import com.greenpaw.chat.LlmService;
import com.greenpaw.disease.DiseaseRecognitionService;
import com.greenpaw.imagegen.ImageGenerationService;
import com.greenpaw.weather.service.WeatherService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolCallingServiceRetrySafetyTest {

    @Test
    void timedOutToolDoesNotReplayIterationAndKeepsHistoryConsistent() throws Exception {
        WeatherService weatherService = mock(WeatherService.class);
        when(weatherService.getWeatherByCity("北京")).thenAnswer(invocation -> {
            Thread.sleep(600);
            return null;
        });
        LlmService llmService = mock(LlmService.class);
        when(llmService.chatWithTools(any(), any()))
                .thenReturn(toolCallResponse(), textResponse("今天北京晴"));

        ToolCallingService service = service(llmService, weatherService, 2000, 100);

        ToolCallResponse response = service.chatWithMessages(
                new JSONArray(), Set.of("getWeather"), AgentContext.anonymous());

        assertEquals("今天北京晴", response.getText());
        assertEquals(1, response.getToolCallHistory().size());
        assertFalse(response.getToolCallHistory().get(0).isSuccess());
        assertTrue(response.getToolCallHistory().get(0).getErrorMessage().contains("超时"));
        verify(weatherService, times(1)).getWeatherByCity("北京");
    }

    @Test
    void llmFailureAfterToolsLeavesToolExecutedOnceAndDegrades() throws Exception {
        WeatherService weatherService = mock(WeatherService.class);
        when(weatherService.getWeatherByCity("北京")).thenReturn(null);
        LlmService llmService = mock(LlmService.class);
        when(llmService.chatWithTools(any(), any()))
                .thenReturn(toolCallResponse())
                .thenThrow(new RuntimeException("LLM 暂时不可用"));

        ToolCallingService service = service(llmService, weatherService, 2000, 10_000);

        ToolCallResponse response = service.chatWithMessages(
                new JSONArray(), Set.of("getWeather"), AgentContext.anonymous());

        assertTrue(response.getText().contains("处理请求时发生错误"));
        assertTrue(response.getText().contains("LLM 暂时不可用"));
        // 工具只执行一次：LLM 重试看到的是已追加的工具结果，绝不重放工具本身
        verify(weatherService, times(1)).getWeatherByCity("北京");
        assertEquals(1, response.getToolCallHistory().size());
        assertTrue(response.getToolCallHistory().get(0).isSuccess());
    }

    @Test
    void pureLlmFailureBeforeToolsStillRetries() throws Exception {
        WeatherService weatherService = mock(WeatherService.class);
        LlmService llmService = mock(LlmService.class);
        when(llmService.chatWithTools(any(), any()))
                .thenThrow(new RuntimeException("网络抖动"))
                .thenReturn(textResponse("已恢复"));

        ToolCallingService service = service(llmService, weatherService, 2000, 10_000);

        ToolCallResponse response = service.chatWithMessages(
                new JSONArray(), Set.of("getWeather"), AgentContext.anonymous());

        assertEquals("已恢复", response.getText());
        assertEquals(2, response.getTotalIterations());
    }

    private ToolCallingService service(LlmService llmService, WeatherService weatherService,
                                       long brokerTimeoutMs, long waitTimeoutMs) {
        SpringAiTools tools = new SpringAiTools(
                weatherService,
                mock(WebSearchTool.class),
                mock(ImageAnalysisTool.class),
                mock(ImageGenerationTool.class),
                mock(ImageEditTool.class),
                mock(CareReminderService.class),
                mock(CareRecordService.class),
                mock(CareAdvancedService.class),
                mock(PetCareQueryService.class),
                mock(PlantSafetyQueryService.class),
                mock(PetFoodSafetyService.class),
                mock(NearbyServiceSearchService.class),
                mock(DiseaseRecognitionService.class),
                mock(UserSessionService.class),
                mock(ArtifactService.class),
                mock(ImageGenerationService.class)
        );
        ToolBroker toolBroker = new ToolBroker(
                tools,
                mock(com.greenpaw.ai.AgentCareTools.class),
                mock(ToolTraceRepository.class),
                mock(ActionConfirmationRepository.class),
                brokerTimeoutMs,
                0
        );
        return new ToolCallingService(llmService, toolBroker, waitTimeoutMs);
    }

    private JSONObject toolCallResponse() {
        JSONObject function = new JSONObject();
        function.put("name", "getWeather");
        function.put("arguments", "{\"city\":\"北京\"}");

        JSONObject call = new JSONObject();
        call.put("id", "call_1");
        call.put("type", "function");
        call.put("function", function);
        JSONArray toolCalls = new JSONArray();
        toolCalls.add(call);

        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        message.put("tool_calls", toolCalls);

        JSONObject choice = new JSONObject();
        choice.put("message", message);

        JSONObject response = new JSONObject();
        response.put("choices", new JSONArray(List.of(choice)));
        return response;
    }

    private JSONObject textResponse(String text) {
        JSONObject message = new JSONObject();
        message.put("role", "assistant");
        message.put("content", text);

        JSONObject choice = new JSONObject();
        choice.put("message", message);

        JSONObject response = new JSONObject();
        response.put("choices", new JSONArray(List.of(choice)));
        return response;
    }
}
