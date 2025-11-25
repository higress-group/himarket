package com.alibaba.apiopenplatform.converter;

import javax.persistence.Converter;
import java.util.List;

@Converter(autoApply = true)
public class ListJsonConverter<E> extends JsonConverter<List<E>> {

    protected ListJsonConverter() {
        super((Class<List<E>>) (Class<?>) List.class);
    }
}
