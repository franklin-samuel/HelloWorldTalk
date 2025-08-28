package com.example.talkVideoAPI.controller;

import com.example.talkVideoAPI.model.SupportedLanguage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class LanguageController {

    @GetMapping("/languages")
        public List<LanguageDTO> getLanguages() {
            return Arrays.stream(SupportedLanguage.values())
                    .map(lang -> new LanguageDTO(lang.getCode(), lang.getDisplayName()))
                    .collect(Collectors.toList());
        }

        public record LanguageDTO(String code, String name) {}

}
