package org.churchband.web;

import java.util.Arrays;
import java.util.List;

import org.churchband.domain.Role;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    @GetMapping
    public List<String> listAll() {
        return Arrays.stream(Role.values())
                .map(Enum::name)
                .toList();
    }
}