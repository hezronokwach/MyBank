package com.example.mybank.repository;

import com.example.mybank.entity.Account;
import com.example.mybank.entity.Transaction;
import com.example.mybank.enums.TransactionType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    boolean existsByToAccountAndType(Account toAccount, TransactionType type);

    // Both sides are fetched in the same query so their account numbers can be read without extra selects
    @Query("""
            select t from Transaction t
            left join fetch t.fromAccount f
            left join fetch t.toAccount ta
            where f.id = :accountId or ta.id = :accountId
            order by t.createdAt desc
            """)
    List<Transaction> findRecentForAccount(@Param("accountId") UUID accountId, Pageable pageable);

    // The money moved onto and off one card, newest first
    @Query("""
            select t from Transaction t
            left join fetch t.fromAccount
            left join fetch t.toAccount
            where t.card.id = :cardId
            order by t.createdAt desc
            """)
    List<Transaction> findRecentForCard(@Param("cardId") UUID cardId, Pageable pageable);

    @Query("""
            select t from Transaction t
            left join fetch t.fromAccount
            left join fetch t.toAccount
            where t.reference = :reference
            """)
    Optional<Transaction> findByReferenceWithAccounts(@Param("reference") String reference);
}
