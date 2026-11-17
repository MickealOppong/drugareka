package com.pages.dto;

import com.pages.enums.ListingStatus;
import com.pages.model.AppUserRole;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Data
@Builder
public class AdminDashboardDto {

    private AdminDashboardStatsDto stats;
    private List<RecentListingDto> recentListings;
    private List<RecentUserDto> recentUsers;

    @Data
    @Builder
    public static class RecentListingDto {
        private Long id;
        private String product;
        private String seller;
        private BigDecimal price;
        private ListingStatus status;
    }

    @Data
    @Builder
    public static class RecentUserDto {
        private Long id;
        private String name;
        private String username;
        private Set<String> role;
    }
}
