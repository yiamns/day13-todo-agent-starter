package com.afs.restapi.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI Agent Planning Service
 * <p>
 * This service demonstrates the core workflow of the AI Agent pattern:
 * 1. Perceive Environment: Load available tools and user input
 * 2. Think & Decide: Use AI model to analyze requirements and create execution plan
 * 3. Execute Actions: Call corresponding tools according to the plan
 * 4. Feedback & Learn: Collect execution results and return to user
 * <p>
 * This pattern enables AI to:
 * - Understand user intent
 * - Select appropriate tools
 * - Create execution strategies
 * - Coordinate multiple tools to complete complex tasks
 */
@Service
public class PlannerService {

    private static final Logger logger = LoggerFactory.getLogger(PlannerService.class);

    private final ChatClient chatClient;
    private final ToolLoader toolLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PlannerService(ChatClient.Builder chatClientBuilder, ToolLoader toolLoader) {
        this.chatClient = chatClientBuilder.build();
        this.toolLoader = toolLoader;
    }

    /**
     * AI Agent's main planning method
     * <p>
     * ASCII Architecture Diagram:
     * <p>
     * ┌─────────────────────────────────────────────────────────────────┐
     * │                        AI Agent Workflow                        │
     * ├─────────────────────────────────────────────────────────────────┤
     * │                                                                 │
     * │  User Input ──┐                                                │
     * │                │                                                │
     * │                ▼                                                │
     * │  ┌─────────────────────────────────────────────────────────┐   │
     * │  │                    Phase 1: Tool Discovery              │   │
     * │  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │   │
     * │  │  │   Tool 1    │  │   Tool 2    │  │   Tool N    │     │   │
     * │  │  │ (Name, Desc,│  │ (Name, Desc,│  │ (Name, Desc,│     │   │
     * │  │  │  Schema)    │  │  Schema)    │  │  Schema)    │     │   │
     * │  │  └─────────────┘  └─────────────┘  └─────────────┘     │   │
     * │  └─────────────────────────────────────────────────────────┘   │
     * │                              │                                │
     * │                              ▼                                │
     * │  ┌─────────────────────────────────────────────────────────┐   │
     * │  │                  Phase 2: Plan Creation                 │   │
     * │  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │   │
     * │  │  │   System    │  │     AI      │  │   Parsed    │     │   │
     * │  │  │   Prompt    │──│   Model     │──│    Plan     │     │   │
     * │  │  │ (Tools +    │  │ (Analyze    │  │ (JSON or    │     │   │
     * │  │  │  Context)   │  │  Input)     │  │ Markdown)   │     │   │
     * │  │  └─────────────┘  └─────────────┘  └─────────────┘     │   │
     * │  └─────────────────────────────────────────────────────────┘   │
     * │                              │                                │
     * │                              ▼                                │
     * │  ┌─────────────────────────────────────────────────────────┐   │
     * │  │                 Phase 3: Plan Execution                 │   │
     * │  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │   │
     * │  │  │   Step 1    │  │   Step 2    │  │   Step N    │     │   │
     * │  │  │ (Tool Call) │  │ (Tool Call) │  │ (Tool Call) │     │   │
     * │  │  └─────────────┘  └─────────────┘  └─────────────┘     │   │
     * │  └─────────────────────────────────────────────────────────┘   │
     * │                              │                                │
     * │                              ▼                                │
     * │                    Final Results                              │
     * │                                                                 │
     * └─────────────────────────────────────────────────────────────────┘
     * <p>
     * This method demonstrates the AI Agent's complete workflow:
     * 1. Tool Discovery Phase: Collect all available tools
     * 2. Plan Creation Phase: AI analyzes requirements and creates execution strategy
     * 3. Plan Execution Phase: Execute tool calls according to the plan
     *
     * @param input User input describing the requirements
     * @return Execution results
     */
    public String plan(String input) {
        logger.info("Starting AI Agent planning workflow, user input: {}", input);

        // Phase 1: Tool Discovery - AI Agent needs to know what tools it can use
        List<ToolCallback> availableTools = discoverAvailableTools();

        // Phase 2: Plan Creation - AI analyzes requirements and creates execution strategy
        logger.info("Phase 2: Creating execution plan");

        // Step 2.1: Prepare AI system prompt
        String systemPrompt = prepareSystemPrompt(input, availableTools);

        // Step 2.2: Call AI model to create plan
        String aiResponse = callAIModelForPlanning(input, systemPrompt);

        // Step 2.3: Parse AI response to extract execution plan
        PlanTaskResult executionPlan = parseAIResponse(aiResponse);

        logger.info("AI created {} execution steps", executionPlan.getPlans().size());

        // Phase 3: Plan Execution - Execute tool calls according to AI's plan
        String executionResult = executeExecutionPlan(executionPlan);

        logger.info("AI Agent planning workflow completed");
        
        // Phase 4: according User's input and the execution result, generate final result
        String finalResult = callLLMForFinalResult(input, executionResult, executionPlan);
        logger.info("Phase 4: Final result generated by LLM");

        return finalResult;
    }

    /**
     * Phase 1: Tool Discovery
     * <p>
     * AI Agent first needs to understand what tools it can use.
     * This is like a human checking their toolbox before starting work.
     *
     * @return List of available tools
     */
    private List<ToolCallback> discoverAvailableTools() {
        logger.info("Phase 1: Discovering available tools");

        List<ToolCallback> generalTools = toolLoader.getGeneralTools();
        logger.info("Discovered {} available tools", generalTools.size());

        // Log basic information for each tool to help understanding
        for (ToolCallback tool : generalTools) {
            logger.debug("Tool: {} - {}",
                    tool.getToolDefinition().name(),
                    tool.getToolDefinition().description());
        }

        return generalTools;
    }

    /**
     * Prepare AI system prompt
     * <p>
     * System prompt tells AI:
     * - Its role and responsibilities
     * - Available tools and their usage
     * - Expected output format
     *
     * @param input          User input
     * @param availableTools Available tools
     * @return Complete system prompt
     */
    private String prepareSystemPrompt(String input, List<ToolCallback> availableTools) {
        // Load prompt template
        String planTemplate = loadPromptTemplate();

        // Inject tool information into template
        String toolsDescription = formatToolsDescription(availableTools);
        String systemPrompt = planTemplate
                .replace("{{ $functions }}", toolsDescription)
                .replace("{{$functions}}", toolsDescription)
                .replace("{{ $input }}", input)
                .replace("{{$input}}", input);

        logger.debug("System prompt prepared, containing {} tool descriptions", availableTools.size());
        return systemPrompt;
    }

    /**
     * Format tool descriptions
     * <p>
     * Convert tool information to a format that AI can understand,
     * including tool name, description, and input parameter schema
     */
    private String formatToolsDescription(List<ToolCallback> tools) {
        return tools.stream()
                .map(tool -> {
                    String name = tool.getToolDefinition().name();
                    String description = tool.getToolDefinition().description();
                    String schema = tool.getToolDefinition().inputSchema();

                    return String.format(
                            "{\"function\":\"%s\",\"description\":\"%s\",\"schema\":\"%s\"}",
                            name, description, schema
                    );
                })
                .collect(Collectors.joining());
    }

    /**
     * Call AI model to create plan
     * <p>
     * This is the "brain" part of the AI Agent:
     * AI analyzes user requirements, understands available tools, then creates execution strategy
     */
    private String callAIModelForPlanning(String input, String systemPrompt) {
        logger.info("Calling AI model to create execution plan");

        String response = chatClient.prompt()
                .system(systemPrompt)
                .user(input)
                .call()
                .content();

        logger.debug("AI model response: {}", response);
        return response;
    }

    /**
     * Parse AI response
     * <p>
     * AI may return plans in multiple formats:
     * 1. Pure JSON format
     * 2. JSON in Markdown code blocks
     * <p>
     * This method is responsible for parsing these formats and extracting structured execution plans
     */
    private PlanTaskResult parseAIResponse(String response) {
        logger.info("Parsing AI response to extract execution plan");

        // Try to parse as direct JSON
        if (response.trim().startsWith("{")) {
            return parseAsDirectJson(response);
        }

        // Try to extract JSON from Markdown code blocks
        return parseFromMarkdownCodeBlocks(response);
    }

    /**
     * Parse direct JSON response
     */
    private PlanTaskResult parseAsDirectJson(String response) {
        try {
            JsonNode jsonNode = objectMapper.readTree(response);
            PlanInfo planInfo = objectMapper.treeToValue(jsonNode, PlanInfo.class);
            logger.info("Successfully parsed JSON format execution plan");
            return new PlanTaskResult(Collections.singletonList(planInfo));
        } catch (JsonProcessingException e) {
            logger.error("JSON parsing failed: {}", response, e);
            throw new RuntimeException("Unable to parse AI's JSON response", e);
        }
    }

    /**
     * Parse execution plan from Markdown code blocks
     */
    private PlanTaskResult parseFromMarkdownCodeBlocks(String response) {
        logger.info("Parsing execution plan from Markdown code blocks");

        Parser parser = Parser.builder().build();
        Node document = parser.parse(response);

        List<FencedCodeBlock> codeBlocks = extractCodeBlocks(document);
        List<PlanInfo> planInfos = parseCodeBlocksToPlans(codeBlocks);

        logger.info("Parsed {} execution plans from {} code blocks",
                planInfos.size(), codeBlocks.size());

        return new PlanTaskResult(planInfos);
    }

    /**
     * Extract code blocks from Markdown document
     */
    private List<FencedCodeBlock> extractCodeBlocks(Node document) {
        List<FencedCodeBlock> codeBlocks = new ArrayList<>();
        Node node = document.getFirstChild();

        while (node != null) {
            if (node instanceof FencedCodeBlock) {
                codeBlocks.add((FencedCodeBlock) node);
            }
            node = node.getNext();
        }

        return codeBlocks;
    }

    /**
     * Parse code blocks to execution plans
     */
    private List<PlanInfo> parseCodeBlocksToPlans(List<FencedCodeBlock> codeBlocks) {
        List<PlanInfo> planInfos = new ArrayList<>();

        for (FencedCodeBlock codeBlock : codeBlocks) {
            String code = codeBlock.getLiteral();
            try {
                PlanInfo planInfo = objectMapper.readValue(code, PlanInfo.class);
                planInfos.add(planInfo);
                logger.debug("Successfully parsed code block: {}", planInfo.getFunctionName());
            } catch (Exception e) {
                logger.warn("Failed to parse code block, skipping: {}", code, e);
            }
        }

        return planInfos;
    }

    /**
     * Phase 3: Plan Execution
     * <p>
     * Execute tool calls according to AI's plan.
     * This is like following a recipe step by step.
     *
     * @param executionPlan Execution plan
     * @return Execution results
     */
    private String executeExecutionPlan(PlanTaskResult executionPlan) {
        logger.info("Phase 3: Executing execution plan");

        if (executionPlan.getPlans().isEmpty()) {
            logger.warn("No execution plan to execute");
            return null;
        }

        // Prepare tool mapping table for fast lookup
        Map<String, ToolCallback> toolMap = prepareToolMap();

        // Execute each plan step sequentially
        StringBuilder results = new StringBuilder();
        for (int i = 0; i < executionPlan.getPlans().size(); i++) {
            PlanInfo plan = executionPlan.getPlans().get(i);
            logger.info("Executing step {}/{}: {}", i + 1, executionPlan.getPlans().size(), plan.getFunctionName());

            String stepResult = executeSinglePlanStep(plan, toolMap);
            if (stepResult != null) {
                if (!results.isEmpty()) {
                    results.append("\n");
                }

                results.append("Plan ").append(i + 1)
                        .append(plan)
                        .append("\nResult : ").append(stepResult);
            }
        }

        String finalResult = "Results:\n" + results;
        logger.info("Execution plan completed, result length: {}", finalResult != null ? finalResult.length() : 0);
        return finalResult;
    }

    /**
     * Prepare tool mapping table
     * <p>
     * Create mapping from tool name to tool instance,
     * improving lookup efficiency during execution
     */
    private Map<String, ToolCallback> prepareToolMap() {
        List<ToolCallback> availableTools = toolLoader.getAllAvailableTools();
        Map<String, ToolCallback> toolMap = new HashMap<>();

        for (ToolCallback tool : availableTools) {
            toolMap.put(tool.getToolDefinition().name(), tool);
        }

        logger.debug("Tool mapping table prepared, containing {} tools", toolMap.size());
        return toolMap;
    }

    /**
     * Execute single plan step
     * <p>
     * Each step represents a tool call:
     * 1. Find corresponding tool
     * 2. Prepare input parameters
     * 3. Execute tool call
     * 4. Handle execution results
     */
    private String executeSinglePlanStep(PlanInfo plan, Map<String, ToolCallback> toolMap) {
        String toolName = plan.getFunctionName();
        ToolCallback tool = toolMap.get(toolName);

        if (tool == null) {
            logger.warn("Tool not found: {}", toolName);
            return String.format("Tool not found: %s", toolName);
        }

        try {
            // Prepare tool input parameters
            String toolInput = objectMapper.writeValueAsString(plan.getVariables());
            logger.info("Executing tool: {}, input parameters: {}", toolName, toolInput);

            // Execute tool call
            String toolResult = tool.call(toolInput);
            logger.info("Tool {} executed successfully", toolName);

            return toolResult;

        } catch (Exception e) {
            logger.error("Tool {} execution failed", toolName, e);
            return String.format("Error executing tool %s: %s", toolName, e.getMessage());
        }
    }

    /**
     * Load prompt template from resource file
     */
    private String loadPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource("plan-prompt.txt");
            return resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Unable to load prompt template", e);
            throw new RuntimeException("Unable to load prompt template", e);
        }
    }

    /**
     * Phase 4: Call LLM to Generate Final Result
     * <p>
     * This phase uses LLM to intelligently generate a comprehensive final result
     * based on the user's original input and execution results.
     * <p>
     * The LLM will:
     * 1. Analyze the user's original request
     * 2. Understand the execution plan and results
     * 3. Generate a user-friendly summary
     * 4. Provide insights and recommendations if appropriate
     *
     * @param userInput      The user's original input/request
     * @param executionResult The raw execution results from Phase 3
     * @param executionPlan  The execution plan that was followed
     * @return A comprehensive final result generated by LLM
     */
    private String callLLMForFinalResult(String userInput, String executionResult, PlanTaskResult executionPlan) {
        logger.info("Calling LLM to generate final result for user input: {}", userInput);

        // Prepare the system prompt for final result generation with variable substitution
        String systemPrompt = prepareFinalResultSystemPrompt();

        // Prepare execution plan summary
        String executionPlanSummary = prepareExecutionPlanSummary(executionPlan);

        // Replace variables in the system prompt
        systemPrompt = systemPrompt
                .replace("{{ $userInput }}", userInput)
                .replace("{{$userInput}}", userInput)
                .replace("{{ $executionPlan }}", executionPlanSummary)
                .replace("{{$executionPlan}}", executionPlanSummary)
                .replace("{{ $executionResult }}", executionResult != null ? executionResult : "No execution results")
                .replace("{{$executionResult}}", executionResult != null ? executionResult : "No execution results");

        // Call LLM to generate final result
        String finalResult = chatClient.prompt()
                .system(systemPrompt)
                .user("Please provide a comprehensive summary based on the above information.")
                .call()
                .content();

        logger.info("LLM generated final result, length: {}", finalResult != null ? finalResult.length() : 0);
        return finalResult;
    }

    /**
     * Prepare system prompt for final result generation
     */
    private String prepareFinalResultSystemPrompt() {
        try {
            ClassPathResource resource = new ClassPathResource("summary-prompt.txt");
            return resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Unable to load summary prompt template", e);
            throw new RuntimeException("Unable to load summary prompt template", e);
        }
    }

    /**
     * Prepare execution plan summary for template substitution
     */
    private String prepareExecutionPlanSummary(PlanTaskResult executionPlan) {
        StringBuilder summary = new StringBuilder();
        summary.append("Total of ").append(executionPlan.getPlans().size()).append(" steps executed:\n");

        for (int i = 0; i < executionPlan.getPlans().size(); i++) {
            PlanInfo plan = executionPlan.getPlans().get(i);
            summary.append(i + 1).append(". ").append(plan.getFunctionName());
            if (plan.getDescription() != null && !plan.getDescription().isEmpty()) {
                summary.append(" - ").append(plan.getDescription());
            }
            summary.append("\n");
        }

        return summary.toString();
    }

    /**
     * Execution plan result wrapper class
     */
    public static class PlanTaskResult {
        private final List<PlanInfo> plans;

        public PlanTaskResult(List<PlanInfo> plans) {
            this.plans = plans;
        }

        public List<PlanInfo> getPlans() {
            return plans;
        }
    }
}
