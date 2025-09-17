package com.afs.restapi.service;

import com.afs.restapi.entity.Todo;
import com.afs.restapi.exception.TodoNotFoundException;
import com.afs.restapi.repository.TodoRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class TodoService {

    private final TodoRepository todoRepository;

    public TodoService(TodoRepository todoRepository) {
        this.todoRepository = todoRepository;
    }

    @Tool(description = "Get all todos")
    public List<Todo> findAll() {
        return todoRepository.findAll();
    }

    @Tool(description = "Create new todo. cannot set id, if set id, will be failed")
    public Todo create(Todo todo) {
        return todoRepository.save(todo);
    }

    @Tool(description = "Update todo by id. Should set id, if not call findAll first.")
    public Todo updateById(Integer id, Todo newTodo) {
        Todo todo = todoRepository.findById(id)
                .orElseThrow(() -> new TodoNotFoundException(id));
        if (newTodo.getDone() != null) {
            todo.setDone(newTodo.getDone());
        }
        if (newTodo.getText() != null) {
            todo.setText(newTodo.getText());
        }
        return todoRepository.save(todo);
    }

    @Tool(description = "Delete todo by id")
    public void deleteById(Integer id) {
        if (!todoRepository.existsById(id)) {
            throw new TodoNotFoundException(id);
        }
        todoRepository.deleteById(id);
    }

    @Tool(description = "create Multiple todos,cannot set id, if set id, will be failed")
    public List<Todo> createMultiple(List<Todo> todos) {
        return todoRepository.saveAll(todos);
    }

    @Tool(description = "delete Multiple todos by ids")
    public void deleteMultipleByIds(List<Integer> ids) {
        for (Integer id : ids) {
            if (!todoRepository.existsById(id)) {
                throw new TodoNotFoundException(id);
            }
        }
        todoRepository.deleteAllById(ids);
    }

    @Tool(description = "update Multiple todos by ids. Should set id, if not call findAll first.")
    public List<Todo> updateMultipleByIds(List<Todo> newTodos) {
        return newTodos.stream()
                .map(todo -> {
                    if (todo.getId() == null || !todoRepository.existsById(todo.getId())) {
                        throw new TodoNotFoundException(todo.getId());
                    }
                    updateById(todo.getId(), todo);
                    return todoRepository.findById(todo.getId()).get();
                })
                .collect(Collectors.toList());
    }

}