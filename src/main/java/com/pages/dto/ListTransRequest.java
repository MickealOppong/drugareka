package com.pages.dto;

import com.pages.enums.InventoryStatus;
import com.pages.enums.ListingStatus;
import com.pages.model.InventoryItem;
import com.pages.model.InventoryItemPrice;
import jakarta.persistence.*;
import lombok.*;

import java.util.List;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ListTransRequest {

    private InventoryItem inventory;

    private ListingStatus listingStatus;
}
