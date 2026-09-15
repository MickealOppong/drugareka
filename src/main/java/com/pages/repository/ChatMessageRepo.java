package com.pages.repository;

import com.pages.model.AppUser;
import com.pages.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepo extends JpaRepository<ChatMessage,Long> {

    // Fetches past messages ordered from oldest to newest
    List<ChatMessage> findByMatchIdOrderByCreatedAtAsc(Long matchId);


    Optional<ChatMessage> findFirstByMatchIdOrderByCreatedAtDesc(Long matchId);



    @Query("SELECT m FROM ChatMessage m WHERE (m.sender.id = :sender AND m.receiver.id = :receiver) OR (m.sender.id = :receiver AND m.receiver.id = :sender)")
    List<ChatMessage> findMessages(@Param("sender") Long sender, @Param("receiver") Long receiver);


    @Modifying
    @Query("DELETE FROM ChatMessage c WHERE c.sender.id = :userId OR c.receiver.id = :userId")
    void deleteUserChatHistory(Long userId);
    @Modifying
    @Query( "DELETE FROM ChatMessage c WHERE c.matchId = :matchId")
    void deleteMatchChatHistory(Long matchId);
}
