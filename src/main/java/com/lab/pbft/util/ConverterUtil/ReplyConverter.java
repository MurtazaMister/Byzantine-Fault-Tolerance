package com.lab.pbft.util.ConverterUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.pbft.networkObjects.acknowledgements.Reply;
import com.lab.pbft.networkObjects.communique.PrePrepare;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Converter
public class ReplyConverter implements AttributeConverter<Reply, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Reply reply) {
        try {
            return objectMapper.writeValueAsString(reply);
        } catch (JsonProcessingException e) {
            log.error("Could not serialize PrePrepare object: {}", e.getMessage());
        }
        return null;
    }

    @Override
    public Reply convertToEntityAttribute(String dbData) {
        try {
            return objectMapper.readValue(dbData, Reply.class);
        } catch (JsonProcessingException e) {
            log.error("Could not deserialize PrePrepare object: {}", e.getMessage());
        }
        return null;
    }
}