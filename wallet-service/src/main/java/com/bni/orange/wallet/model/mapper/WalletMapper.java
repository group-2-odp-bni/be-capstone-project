package com.bni.orange.wallet.model.mapper;

import com.bni.orange.wallet.model.entity.Wallet;
import com.bni.orange.wallet.model.entity.read.WalletRead;
import com.bni.orange.wallet.model.request.wallet.WalletCreateRequest;
import com.bni.orange.wallet.model.request.wallet.WalletUpdateRequest;
import com.bni.orange.wallet.model.response.WalletDetailResponse;
import com.bni.orange.wallet.model.response.WalletListItemResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Mapper(componentModel = "spring", injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface WalletMapper {

  @Mapping(target = "defaultForUser", source = "defaultForUser")
  WalletListItemResponse toListItem(WalletRead read);

  @Mapping(target="id",       expression="java(read.getId())")
  @Mapping(target="userId",   expression="java(oltp.getUserId())")
  @Mapping(target="currency", expression="java(oltp.getCurrency())")
  @Mapping(target="status",   expression="java(read.getStatus())")
  @Mapping(target="type",     expression="java(read.getType())")
  @Mapping(target="name",     expression="java(read.getName()!=null ? read.getName() : oltp.getName())")
  @Mapping(target="balanceSnapshot", expression="java(read.getBalanceSnapshot())")
  @Mapping(target="defaultForUser",  source="read.defaultForUser")
  @Mapping(target="metadata",        source="filteredMetadata")
  @Mapping(target="createdAt",       expression="java(oltp.getCreatedAt())")
  @Mapping(target="updatedAt",       expression="java(oltp.getUpdatedAt())")
  WalletDetailResponse mergeDetail(WalletRead read, Wallet oltp, Map<String,Object> filteredMetadata);

  @Mapping(target="id",        expression="java(java.util.UUID.randomUUID())")
  @Mapping(target="userId",    source="userId")
  @Mapping(target="currency",  constant="IDR")
  @Mapping(target="metadata",  expression="java(metadataToString(req.getMetadata()))")
  @Mapping(target="createdAt", expression="java(now())")
  @Mapping(target="updatedAt", expression="java(now())")
  Wallet toEntity(WalletCreateRequest req, UUID userId);

  @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
  @Mapping(target="updatedAt", expression="java(now())")
  void patch(@MappingTarget Wallet wallet, WalletUpdateRequest req);

  default OffsetDateTime now(){ return OffsetDateTime.now(); }

  default String metadataToString(Object metadata) {
    if (metadata == null) {
      return "{}";
    }
    try {
      return new ObjectMapper().writeValueAsString(metadata);
    } catch (JsonProcessingException e) {
      // Handle exception, perhaps log it and return a default value
      return "{}";
    }
  }
}
