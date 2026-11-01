package com.ureclive.urec_live_backend.controller;

import com.ureclive.urec_live_backend.service.GymConnectionService;
import com.ureclive.urec_live_backend.service.GymConnectionService.*;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class GymController {
    private final GymConnectionService gyms;

    public GymController(GymConnectionService gyms) {
        this.gyms = gyms;
    }

    @GetMapping("/gyms")
    public List<GymDto> directory() {
        return gyms.directory();
    }

    @GetMapping("/users/me/gym")
    public GymDto current() {
        return gyms.current();
    }

    @PutMapping("/users/me/gym")
    public GymDto connect(@RequestBody Selection selection) {
        return gyms.connect(selection);
    }

    @DeleteMapping("/users/me/gym")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disconnect() {
        gyms.disconnect();
    }
}
