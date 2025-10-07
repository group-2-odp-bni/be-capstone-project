package com.bni.orange.authentication.base;


public interface NoRespService<T extends Request> {
    void execute(T request);
}
