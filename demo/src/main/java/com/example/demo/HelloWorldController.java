package com.example.demo;

import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloWorldController {
    
    private final ApplicationEventPublisher publisher;
    
    
    public HelloWorldController(ApplicationEventPublisher publisher) {
        super();
        this.publisher = publisher;
    }

    @RequestMapping("/hello")
    public String hello() {
        return "hello world";
    }
    
    @RequestMapping("/down")
    public String down() {
        AvailabilityChangeEvent.publish(publisher, this, ReadinessState.REFUSING_TRAFFIC);
        return "down";
    }
    
    @RequestMapping("/up")
    public String up() {
        AvailabilityChangeEvent.publish(publisher, this, ReadinessState.ACCEPTING_TRAFFIC);
        return "up";
    }
}
