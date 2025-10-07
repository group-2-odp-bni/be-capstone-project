package com.bni.orange.authentication.base;

public interface BaseService<T extends Request, V extends Response> {
    V execute(T request);
}
