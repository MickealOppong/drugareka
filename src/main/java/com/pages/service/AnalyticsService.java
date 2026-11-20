package com.pages.service;

import com.pages.dto.AdminDashboardDto;
import com.pages.dto.AdminDashboardStatsDto;
import com.pages.dto.AnalyticsDto;
import com.pages.dto.SellingActivityResponse;
import com.pages.enums.InventoryStatus;
import com.pages.enums.ListingStatus;
import com.pages.enums.OrderStatus;
import com.pages.enums.ShipmentStatus;
import com.pages.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.plugins.jpeg.JPEGImageReadParam;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnalyticsService {


    private final ListingTransactionService listingTransactionService;

    private final AppUserDetailsService appUserDetailsService;

    private final WishListService wishListService;

    private final ShipmentService shipmentService;
    private final SellerProfileService sellerProfileService;

    private final ListingOrderService listingOrderService;
    private final CategoryService categoryService;
    private final BrandService brandService;
    private final  InventoryItemPriceService inventoryItemPriceService;


    private Long getSellerListingCount(Jwt jwt){

        if(jwt ==null){
            return 0L;
        }
        String username = jwt.getSubject();
        AppUser currentUser = appUserDetailsService.getAppUserByUsername(username);
        SellerProfile sellerProfile = sellerProfileService.getSellerProfile(currentUser.getId());
        if(sellerProfile!=null){
            return listingTransactionService.getSellerPublishedListingCount(sellerProfile.getId());
        }
        return 0L;

    }


    private Long getWishlistCount(Jwt jwt){

        return wishListService.getWishlistCount(jwt);
    }



    private Long itemSold(Jwt jwt){
       return listingOrderService.mySalesCount(jwt);
    }
    private Long itemSoldCancelled(Jwt jwt){
        return listingOrderService.mySalesCancelledCount(jwt);
    }

    private Long orders(Jwt jwt){
        return listingOrderService.myPurchaseCount(jwt);
    }


    private Long awaitingShipmentCount(Jwt jwt){
      return   shipmentService.myShipments(jwt);
    }

    public AnalyticsDto dashboardAnalytics(Jwt jwt){

        BigDecimal payout = sellerProfileService.outStandingPayout(jwt);

        return AnalyticsDto.builder()
                .itemSoldCount(itemSold(jwt))
                .ordersCount(orders(jwt))
                .listingCount(getSellerListingCount(jwt))
                .itemSoldCancelledCount(itemSoldCancelled(jwt))
                .wishlistCount(getWishlistCount(jwt))
                .outstandingPayout(payout)
                .awaitingShipmentCount(awaitingShipmentCount(jwt))
                .build();
    }

    private ListingOrderItem getLastestSaleActivity(Jwt jwt){
        if(jwt ==null){
            return null;
        }
        String username = jwt.getSubject();
        AppUser currentUser = appUserDetailsService.getAppUserByUsername(username);
        SellerProfile sellerProfile = sellerProfileService.getSellerProfile(currentUser.getId());
        if(sellerProfile!=null){
            return listingOrderService.getRecentSaleBySeller(sellerProfile.getId());
        }
     return null;
    }



    private BigDecimal calculatePercentageChange(long current, long previous) {

        if (previous == 0) {
            return current > 0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(previous),
                        2,
                        RoundingMode.HALF_UP
                );
    }


    private BigDecimal calculatePercentageChange(BigDecimal current, BigDecimal previous) {

        if (previous==null || previous.compareTo(BigDecimal.ZERO)==0) {
            return current.compareTo(BigDecimal.ZERO)>0 ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        }

        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        previous,
                        2,
                        RoundingMode.HALF_UP
                );
    }


    @Transactional(readOnly = true)
    public AdminDashboardDto adminDashboard(Jwt jwt){
        if(jwt==null){
            return AdminDashboardDto.builder().build();
        }

        //time marker
        Instant current = Instant.now();
        Instant previous = Instant.now().minus(30, ChronoUnit.DAYS);

        //totalUsers
       long currentUsers= appUserDetailsService.totalUsers(current);
       long previousUsers = appUserDetailsService.totalUsers(previous);
       BigDecimal usersChange= calculatePercentageChange(currentUsers,previousUsers);
       //stats dto
       AdminDashboardStatsDto.StatDto usersStats = AdminDashboardStatsDto.StatDto.builder()
               .increase(currentUsers-previousUsers)
               .previousTotal(previousUsers)
               .total(currentUsers)
               .percentageChange(usersChange)
               .build();

       //recent created users
       List<AdminDashboardDto.RecentUserDto> newUSers = appUserDetailsService.getNewUsersCreated(previous,current)
               .stream().map(newUser->{
                   return  AdminDashboardDto.RecentUserDto.builder()
                           .role(newUser.getRoles())
                           .id(newUser.getUserId())
                           .name(newUser.getFirstName()+" "+newUser.getLastName())
                           .username(newUser.getEmail())
                           .build();
               }).toList();



       //listings
       Long currentListings= listingTransactionService.totalListings(current);
       Long previousListings = listingTransactionService.totalListings(previous);
        BigDecimal listingChange= calculatePercentageChange(currentListings,previousListings);

        //stats dto
        AdminDashboardStatsDto.StatDto listingStats = AdminDashboardStatsDto.StatDto.builder()
                .increase(currentListings-previousListings)
                .previousTotal(previousListings)
                .total(currentListings)
                .percentageChange(listingChange)
                .build();

        //Active listings
        Long currentActiveListings= listingTransactionService.totalActiveListings(ListingStatus.PUBLISHED,current);
        Long previousActiveListings = listingTransactionService.totalActiveListings(ListingStatus.PUBLISHED,previous);
        BigDecimal listingActiveChange= calculatePercentageChange(currentActiveListings,previousActiveListings);

        //stats dro
        AdminDashboardStatsDto.StatDto listingActiveStats = AdminDashboardStatsDto.StatDto.builder()
                .increase(currentActiveListings-previousActiveListings)
                .previousTotal(previousActiveListings)
                .total(currentActiveListings)
                .percentageChange(listingActiveChange)
                .build();

        //recent listings
        List<AdminDashboardDto.RecentListingDto> newListings = listingTransactionService.getNewListings(previous,current)
                .stream().map(newListing->{
                    InventoryItemPrice priceService = inventoryItemPriceService.getPrices(newListing.getInventory().getId());
                    return  AdminDashboardDto.RecentListingDto.builder()
                            .price(priceService.getSellerNewPrice())
                            .id(newListing.getId())
                            .status(newListing.getListingStatus())
                            .product(newListing.getInventory().getProductCatalog().getName())
                            .seller(newListing.getInventory().getSeller().getFullLegalName())
                            .build();
                }).toList();

       //orders
        Long currentOrders = listingOrderService.totalOrders(current);
        Long previousOrders = listingOrderService.totalOrders(previous);
        BigDecimal OrdersChange= calculatePercentageChange(currentOrders,previousOrders);

        //orders dro
        AdminDashboardStatsDto.StatDto ordersStats = AdminDashboardStatsDto.StatDto.builder()
                .increase(currentOrders-previousOrders)
                .previousTotal(previousOrders)
                .total(currentOrders)
                .percentageChange(OrdersChange)
                .build();

        //sales
        BigDecimal currentSales = listingOrderService.totalSales(current,OrderStatus.PAID);
        BigDecimal previousSales = listingOrderService.totalSales(previous,OrderStatus.PAID);
        BigDecimal salesChange= calculatePercentageChange(currentSales,previousSales);

        long currentSalesAmount = currentSales.setScale(0, RoundingMode.HALF_UP).longValueExact();
        long previousSalesAmount = previousSales.setScale(0, RoundingMode.HALF_UP).longValueExact();

        //Sales dro
        AdminDashboardStatsDto.StatDto salesStats = AdminDashboardStatsDto.StatDto.builder()
                .increase( currentSales.subtract(previousSales).longValue())
                .previousTotal(previousSalesAmount)
                .total(currentSalesAmount)
                .percentageChange(salesChange)
                .build();

        //categories
        Long totalCategories = categoryService.totalCategories();
        //category stats
        AdminDashboardStatsDto.StatDto categoryStats = AdminDashboardStatsDto.StatDto.builder()
                .increase( 0)
                .previousTotal(0)
                .total(totalCategories)
                .percentageChange(BigDecimal.ZERO)
                .build();

        //brands
        Long totalBrands = brandService.totalBrand();
        //brand dro
        AdminDashboardStatsDto.StatDto brandStats = AdminDashboardStatsDto.StatDto.builder()
                .increase( 0)
                .previousTotal(0)
                .total(totalBrands)
                .percentageChange(BigDecimal.ZERO)
                .build();

        //sellers
        long currentSellers = sellerProfileService.totalSellers(current);
        long previousSellers = sellerProfileService.totalSellers(previous);
        BigDecimal sellerChange = calculatePercentageChange(currentSellers,previousSellers);

        //stats
        AdminDashboardStatsDto.StatDto sellersStats = AdminDashboardStatsDto.StatDto.builder()
                .increase( currentSellers-previousSellers)
                .previousTotal(previousSellers)
                .total(currentSellers)
                .percentageChange(sellerChange)
                .build();

        AdminDashboardStatsDto adminDashboardStatsDto  = AdminDashboardStatsDto.builder()
                .Sales(salesStats)
                .orders(ordersStats)
                .users(usersStats)
                .sellers(sellersStats)
                .listings(listingActiveStats)
                .brands(brandStats)
                .categories(categoryStats)
                .products(listingStats)
                .build();

        return AdminDashboardDto.builder()
                .recentUsers(newUSers)
                .stats(adminDashboardStatsDto)
                .recentListings(newListings)
                .build();
    }


    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<SellingActivityResponse> recentSellingActivities(Jwt jwt){
        List<SellingActivityResponse> dto = new ArrayList<>();

        //recent sale
        ListingOrderItem orderItem = getLastestSaleActivity(jwt);

        if(orderItem!=null){
            SellingActivityResponse sales= SellingActivityResponse.builder()
                    .id(orderItem.getId())
                    .name(orderItem.getInventoryItem().getProductCatalog().getName())
                    .status(orderItem.getInventoryItem().getStatus().name())
                    .type("sold")
                    .path("/account/orders")
                    .build();
            dto.add(sales);
        }
        //recent shipment
        SellerShipment recentShipment = shipmentService.recentShipment(jwt);
        if(recentShipment!=null){
            SellingActivityResponse shipment= SellingActivityResponse.builder()
                    .id(recentShipment.getId())
                    .name(recentShipment.getListingOrderItem().getInventoryItem().getProductCatalog().getName())
                    .status(recentShipment.getShipmentStatus().name())
                    .type("shipment")
                    .path("/account/shipments")
                    .build();
            dto.add(shipment);
        }

        //recent listing
        ListingTransaction recentListing= listingTransactionService.recentListing(jwt);
        if(recentListing !=null){
            SellingActivityResponse recentItem= SellingActivityResponse.builder()
                    .id(recentListing.getId())
                    .name(recentListing.getInventory().getProductCatalog().getName())
                    .status(recentListing.getListingStatus().name())
                    .type("active")
                    .path("/account/listings/me")
                    .build();
            dto.add(recentItem);
        }
        return dto;

    }
}
