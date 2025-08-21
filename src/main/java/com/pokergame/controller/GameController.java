package com.pokergame.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/game")
@CrossOrigin(origins = "http://localhost:3000")
public class GameController {

    @GetMapping("/test")
    public String test() {
        return "Poker backend is working!";
    }
}
