package com.greenpaw.agent.repository;

import com.greenpaw.agent.domain.ToolTrace;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;;

public interface ToolTraceRepository extends JpaRepository<ToolTrace, Long> {

    List<ToolTrace> findByConversationIdOrderByCreatedAtDesc(String conversationId);
}
