package com.ozichat.call.repository;

import com.ozichat.call.document.CallSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CallSessionRepository extends MongoRepository<CallSession, String> {

    /** Call history for a user — as caller or callee, newest first. */
    @Query("{ $or: [ { 'callerId': ?0 }, { 'calleeId': ?0 } ] }")
    List<CallSession> findByParticipant(Long userId, Pageable pageable);

    /** Missed calls — callee never answered. */
    @Query("{ 'calleeId': ?0, 'state': 'MISSED' }")
    List<CallSession> findMissedCalls(Long calleeId, Pageable pageable);
}
