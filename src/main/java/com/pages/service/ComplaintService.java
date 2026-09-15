package com.pages.service;

import com.pages.dto.ComplaintRequest;
import com.pages.dto.ComplaintResponse;
import com.pages.dto.ListComplaintPage;
import com.pages.dto.ResponseDto;
import com.pages.enums.ComplaintStatus;
import com.pages.model.AppUser;
import com.pages.model.Complaint;
import com.pages.model.ListingOrder;
import com.pages.repository.ComplaintRepo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.UUID;

@Service
public class ComplaintService {

    private final AppUserDetailsService appUserDetailsService;
    private final ListingOrderService listingOrderService;
    private final ComplaintRepo complaintRepo;

    public ComplaintService(AppUserDetailsService appUserDetailsService, ListingOrderService listingOrderService, ComplaintRepo complaintRepo) {
        this.appUserDetailsService = appUserDetailsService;
        this.listingOrderService = listingOrderService;
        this.complaintRepo = complaintRepo;
    }


    public ResponseDto<Boolean> createComplaint(Jwt jwt, ComplaintRequest request){

       try{
           if(jwt!=null){
               AppUser appUser = appUserDetailsService.getAppUserByUsername(jwt.getSubject());

               // 1. Generate a distinct, cryptographic public tracking reference string (Receipt)
               String ticketNumber = "TK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

               ListingOrder listingOrder= listingOrderService.getOrderById(request.getOrderId());

               Complaint complaint = Complaint.builder()
                       .complaintStatus(ComplaintStatus.OPEN)
                       .description(request.getDescription())
                       .issue(request.getIssue())
                       .user(appUser)
                       .orderNumber(listingOrder.getOrderNumber())
                       .ticketId(ticketNumber)
                       .build();
               complaintRepo.save(complaint);
               return ResponseDto.<Boolean>builder()
                       .data(true)
                       .message("Created")
                       .httpStatus(HttpStatus.OK.value())
                       .build();
           }
           return ResponseDto.<Boolean>builder()
                   .data(false)
                   .message("Unable to register complaint")
                   .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                   .build();
       }catch (Exception e){
            return ResponseDto.<Boolean>builder()
                    .data(false)
                    .message(e.getMessage())
                    .httpStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())
                    .build();
       }

    }


    public ListComplaintPage allComplaints(Jwt jwt,int page,int size){

       if(jwt!=null){
           Pageable pageable = PageRequest.of(page-1,
                   size,
                   Sort.by(Sort.Direction.DESC, "createdAt")
           );
           Page<ComplaintResponse>  complaintResponses =complaintRepo.findAll(pageable)
                   .map(complaint -> {
                       return ComplaintResponse.builder()
                               .id(complaint.getId())
                               .status(complaint.getComplaintStatus())
                               .issue(complaint.getIssue())
                               .ticketId(complaint.getTicketId())
                               .orderNumber(complaint.getOrderNumber())
                               .user(complaint.getUser().getUsername())
                               .description(complaint.getDescription())
                               .createdAt(complaint.getCreatedAt())
                               .build();
                   });
           return ListComplaintPage.builder()
                   .complaintResponseList(complaintResponses.getContent())
                   .page(complaintResponses.getNumber())
                   .pageSize(complaintResponses.getSize())
                   .totalElements(complaintResponses.getTotalElements())
                   .build();
       }
       return ListComplaintPage.builder().build();
    }
}
