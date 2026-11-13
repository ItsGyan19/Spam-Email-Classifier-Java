package com.classifier.spam.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ClassificationController {
    @GetMapping("/")
    public String home() {
        return "index";
    }
}
