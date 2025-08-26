package com.pokergame;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.awt.Desktop;
import java.net.URI;

@SpringBootApplication
public class PokerBackendApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(PokerBackendApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String url = "http://localhost:8080";
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                desktop.browse(new URI(url));
                System.out.println("Opening browser at: " + url);
            }
        }
    }
}
