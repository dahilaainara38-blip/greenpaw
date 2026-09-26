package com.greenpaw.ai;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.greenpaw.agent.domain.ActionConfirmation;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolCallingServiceAgentContextTest {

    private CareReminderService careReminderService;
    private ActionConfirmationRepository confirmationRepository;
    private ToolCallingService toolCallingService;

    @BeforeEach
    void setUp() {
        careReminderService = mock(CareReminderService.class);
        confirmationRepository = mock(ActionConfirmationRepository.class);

        SpringAiTools tools = new SpringAiTools(
                mock(WeatherService.class),
                mock(WebSearchTool.class),
                mock(ImageAnalysisTool.class),
                mock(ImageGenerationTool.class),
                mock(ImageEditTool.class),
                careReminderService,
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
                confirmationRepository,
                1000,
                0
        );
        toolCallingService = new ToolCallingService(mock(LlmService.class), toolBroker, 10_000);
    }

    @Test
    void agentContextIsHiddenFromToolSchema() {
        JSONArray schema = toolCallingService.buildToolsSchema(
                Set.of("completeCareReminder"),
                new AgentContext("user-1", "conversation-1", "PET", 12L)
        );

        JSONObject function = schema.getJSONObject(0).getJSONObject("function");
        JSONObject properties = function.getJSONObject("parameters").getJSONObject("properties");
        JSONArray required = function.getJSONObject("parameters").getJSONArray("required");

        assertFalse(properties.containsKey("context"));
        assertFalse(properties.containsKey("userId"));
        assertTrue(required == null || required.isEmpty());
    }

    @Test
    void brokerRegistersAllLegacyTools() {
        assertEquals(25, toolCallingService.getRegisteredToolNames().size());
    }

    @Test
    void agentContextIsInjectedIntoToolExecution() {
        AgentContext context = new AgentContext(
                "user-1",
                "conversation-1",
                "PET",
                12L,
                Set.of(AgentContext.Permission.AGENT_WRITE),
                1L,
                null
        );
        when(careReminderService.completeLatestReminder("user-1")).thenReturn("提醒已完成");

        String result = toolCallingService.executeTool(
                "completeCareReminder",
                new JSONObject(),
                context
        );

        assertTrue(result.contains("提醒已完成"));
        verify(careReminderService).completeLatestReminder("user-1");
    }

    @Test
    void writeWithoutConfirmationCreatesPendingAction() {
        AgentContext context = new AgentContext(
                "user-1",
                "conversation-1",
                "PET",
                12L
        );
        when(confirmationRepository.save(any())).thenAnswer(invocation -> {
            ActionConfirmation confirmation = invocation.getArgument(0);
            confirmation.setId(9L);
            return confirmation;
        });

        JSONObject arguments = new JSONObject();
        arguments.put("targetType", "PET");
        arguments.put("targetId", 12L);
        arguments.put("reminderType", "用药");
        arguments.put("content", "给咪咪滴耳药");
        arguments.put("dueAt", "2026-08-30 08:00");

        String result = toolCallingService.executeTool("createCareReminder", arguments, context);

        assertTrue(result.contains("[CONFIRMATION:9]"));
        var captor = org.mockito.ArgumentCaptor.forClass(ActionConfirmation.class);
        verify(confirmationRepository).save(captor.capture());
        assertEquals(ActionConfirmation.Status.PENDING, captor.getValue().getStatus());
        assertEquals("REMINDER_CREATE", captor.getValue().getActionType());
    }

    @Test
    void saveDiagnosisIsBrokeredWriteAndCreatesConfirmation() {
        AgentContext context = new AgentContext(
                "user-1",
                "conversation-1",
                "PET",
                12L
        );
        when(confirmationRepository.save(any())).thenAnswer(invocation -> {
            ActionConfirmation confirmation = invocation.getArgument(0);
            confirmation.setId(9L);
            return confirmation;
        });

        JSONObject arguments = new JSONObject();
        arguments.put("type", "plant");
        arguments.put("diseaseName", "白粉病");
        arguments.put("confidence", "HIGH");

        String result = toolCallingService.executeTool("saveDiagnosis", arguments, context);

        assertTrue(result.contains("[CONFIRMATION:9]"));
        var captor = org.mockito.ArgumentCaptor.forClass(ActionConfirmation.class);
        verify(confirmationRepository).save(captor.capture());
        assertEquals(ActionConfirmation.Status.PENDING, captor.getValue().getStatus());
        assertEquals("DIAGNOSIS_SAVE", captor.getValue().getActionType());
    }
}
