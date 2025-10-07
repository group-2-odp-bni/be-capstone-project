package com.bni.orange.authentication.base;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;

@SuppressWarnings("unused")
@JsonTypeName("data")
@JsonTypeInfo(include = JsonTypeInfo.As.WRAPPER_OBJECT, use = JsonTypeInfo.Id.NAME)
public interface BaseDataResponse extends Response {
}
