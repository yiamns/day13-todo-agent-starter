package com.afs.restapi.dto;

/**
 * 计划请求 DTO
 */
public class PlanRequestDto {
    
    private String userInput;
    
    public PlanRequestDto() {}
    
    public PlanRequestDto(String userInput) {
        this.userInput = userInput;
    }
    
    public String getUserInput() {
        return userInput;
    }
    
    public void setUserInput(String userInput) {
        this.userInput = userInput;
    }
    
    @Override
    public String toString() {
        return "PlanRequestDto{" +
                "userInput='" + userInput + '\'' +
                '}';
    }
}
