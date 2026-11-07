package com.pages.dto;


public record QueryDto(String category,String search,String condition,Boolean isCollectionAvailable,String collectionCity,String brand,Integer page, Integer size) {
}
