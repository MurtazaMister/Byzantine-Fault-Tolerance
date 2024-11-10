package com.lab.pbft.util.ConverterUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.pbft.networkObjects.communique.PrePrepare;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

@Converter
@Slf4j
public class PrePrepareConverter implements AttributeConverter<PrePrepare, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(PrePrepare prePrepare) {
        try {
            if(prePrepare == null){
                return "{}";
            }
            return objectMapper.writeValueAsString(prePrepare);
        } catch (JsonProcessingException e) {
            log.error("Could not serialize PrePrepare object: {}", e.getMessage());
        }
        return "{}";
    }

    @Override
    public PrePrepare convertToEntityAttribute(String dbData) {
        try {
            if(dbData == null || dbData.trim().isEmpty()){
                return new PrePrepare();
            }
            return objectMapper.readValue(dbData, PrePrepare.class);
        } catch (JsonProcessingException e) {
            log.error("Could not deserialize PrePrepare object: {}", e.getMessage());
        }
        return new PrePrepare();
    }
}
