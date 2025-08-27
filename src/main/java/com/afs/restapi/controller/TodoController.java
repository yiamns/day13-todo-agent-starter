package com.afs.restapi.controller;

import com.afs.restapi.entity.Todo;
import com.afs.restapi.service.TodoService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/todos")
public class TodoController {

    private final TodoService todoService;
    public TodoController(TodoService todoService) {
        this.todoService = todoService;
    }

    @GetMapping
    List<Todo> getAll() {
        return todoService.findAll();
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    Todo create(@RequestBody Todo todo) {
        return todoService.create(todo);
    }

    @PutMapping("/{id}")
    Todo updateById(@PathVariable Integer id, @RequestBody Todo request) {
        return todoService.updateById(id, request);
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{id}")
    void delete(@PathVariable Integer id) {
        todoService.deleteById(id);
    }
}
