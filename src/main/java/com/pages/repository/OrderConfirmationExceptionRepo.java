package com.pages.repository;

import com.pages.model.OrderConfirmationException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderConfirmationExceptionRepo extends JpaRepository<OrderConfirmationException,Long> {
}
