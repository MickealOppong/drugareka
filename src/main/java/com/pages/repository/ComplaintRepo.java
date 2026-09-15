package com.pages.repository;

import com.pages.model.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplaintRepo extends JpaRepository<Complaint,Long> {

}
