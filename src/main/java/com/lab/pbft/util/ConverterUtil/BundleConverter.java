package com.lab.pbft.util.ConverterUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.lab.pbft.model.primary.NewView;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

@Converter
@Slf4j
public class BundleConverter implements AttributeConverter<NewView.Bundle, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(NewView.Bundle bundle) {
        if (bundle == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(bundle);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    @Override
    public NewView.Bundle convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new NewView.Bundle();
        }
        try {
            return objectMapper.readValue(dbData, NewView.Bundle.class);
        } catch (JsonProcessingException e) {
            return new NewView.Bundle();
        }
    }
}
