package com.bni.orange.authentication.base;

public interface NoArgService<V extends Response> {
    V execute();
}
