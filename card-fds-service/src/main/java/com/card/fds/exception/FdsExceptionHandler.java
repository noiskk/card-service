package com.card.fds.exception;

import com.card.fds.controller.FdsController;
import org.springframework.hateoas.EntityModel;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestControllerAdvice
public class FdsExceptionHandler {

    @ExceptionHandler(FdsException.class)
    public ResponseEntity<EntityModel<Map<String, String>>> handleFdsException(FdsException ex) {
        // 에러 메시지 구성
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", "Forbidden");
        errorResponse.put("message", ex.getMessage());

        // HATEOAS EntityModel 생성
        EntityModel<Map<String, String>> entityModel = EntityModel.of(errorResponse);

        // 재시도할 수 있는 링크(self) 제공
        entityModel.add(linkTo(methodOn(FdsController.class).inspect(null)).withSelfRel());

        // 403 Forbidden 상태 코드로 반환
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(entityModel);
    }
}