package org.comroid.mcsd.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Getter
@RequiredArgsConstructor
@Converter(autoApply = true)
public class JacksonObjectConverter<T> implements AttributeConverter<T, String> {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Class<T> type;

    @Override
    @SneakyThrows
    public String convertToDatabaseColumn(T t) {
        return mapper.writeValueAsString(t);
    }

    @Override
    @SneakyThrows
    public T convertToEntityAttribute(String s) {
        return mapper.readValue(s, type);
    }
}
