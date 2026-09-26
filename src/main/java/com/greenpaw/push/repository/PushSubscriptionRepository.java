package com.greenpaw.push.repository;

import com.greenpaw.push.entity.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {
    List<PushSubscription> findByUserId(String userId);
    void deleteByEndpoint(String endpoint);
}
