package com.greenpaw.ai;

import com.alibaba.fastjson2.JSONObject;
import com.greenpaw.agent.repository.ActionConfirmationRepository;
import com.greenpaw.agent.repository.ToolTraceRepository;
import com.greenpaw.agent.service.ArtifactService;
import com.greenpaw.agent.tools.ImageAnalysisTool;
import com.greenpaw.agent.tools.ImageEditTool;
import com.greenpaw.agent.tools.ImageGenerationTool;
import com.greenpaw.agent.tools.WebSearchTool;
import com.greenpaw.care.service.CareAdvancedService;
import com.greenpaw.care.service.CareRecordService;
import com.greenpaw.care.service.CareReminderService;
import com.greenpaw.care.service.NearbyServiceSearchService;
import com.greenpaw.care.service.PetCareQueryService;
import com.greenpaw.care.service.PetFoodSafetyService;
import com.greenpaw.care.service.PlantSafetyQueryService;
import com.greenpaw.chat.UserSessionService;
import com.greenpaw.disease.DiseaseRecognitionService;
import com.greenpaw.imagegen.ImageGenerationService;
import com.greenpaw.weather.service.WeatherService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ToolBrokerRateLimitTest {

    @Test
    void exceedingPerUserLimitIsRejectedWhileOtherUsersProceed() {
        ToolBroker broker = broker(2);

        AgentContext heavy = new AgentContext("heavy-user", "conv", null, null);
        AgentContext other = new AgentContext("other-user", "conv", null, null);

        assertTrue(broker.execute("getCurrentTime", new JSONObject(), null, heavy).contains("20"));
        assertTrue(broker.execute("getCurrentTime", new JSONObject(), null, heavy).contains("20"));

        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> broker.execute("getCurrentTime", new JSONObject(), null, heavy));
        assertTrue(rejected.getMessage().contains("每分钟上限 2"));

        // 其他用户不受影响
        assertTrue(broker.execute("getCurrentTime", new JSONObject(), null, other).contains("20"));
    }

    @Test
    void zeroLimitDisablesRateLimiting() {
        ToolBroker broker = broker(0);
        AgentContext user = new AgentContext("user", "conv", null, null);

        for (int i = 0; i < 5; i++) {
            assertTrue(broker.execute("getCurrentTime", new JSONObject(), null, user).contains("20"));
        }
    }

    private ToolBroker broker(long callsPerMinute) {
        SpringAiTools tools = new SpringAiTools(
                mock(WeatherService.class),
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
        return new ToolBroker(
                tools,
                mock(com.greenpaw.ai.AgentCareTools.class),
                mock(ToolTraceRepository.class),
                mock(ActionConfirmationRepository.class),
                1000,
                callsPerMinute
        );
    }
}
