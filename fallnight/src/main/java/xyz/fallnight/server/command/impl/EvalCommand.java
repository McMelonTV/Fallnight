package xyz.fallnight.server.command.impl;

import xyz.fallnight.server.command.framework.CommandMessages;
import xyz.fallnight.server.command.framework.FallnightCommand;
import xyz.fallnight.server.command.framework.PermissionService;
import java.text.DecimalFormat;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.CommandResult;
import net.minestom.server.command.builder.arguments.ArgumentType;

public final class EvalCommand extends FallnightCommand {
    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0.########");

    public EvalCommand(PermissionService permissionService) {
        super("eval", permissionService);

        var inputArg = ArgumentType.StringArray("input");

        setDefaultExecutor((sender, context) -> {
            sender.sendMessage(CommandMessages.info("Usage: /eval <expression...>"));
            sender.sendMessage(CommandMessages.info("Tip: prefix with / to execute as console command."));
        });

        addSyntax((sender, context) -> {
            String input = String.join(" ", context.get(inputArg)).trim();
            if (input.isBlank()) {
                sender.sendMessage(CommandMessages.error("Usage: /eval <expression...>"));
                return;
            }

            if (input.startsWith("/")) {
                String commandInput = input.substring(1).trim();
                if (commandInput.isBlank()) {
                    sender.sendMessage(CommandMessages.error("Cannot execute an empty command."));
                    return;
                }
                CommandResult result = MinecraftServer.getCommandManager().executeServerCommand(commandInput);
                sender.sendMessage(CommandMessages.success("Executed command with result: " + result.getType()));
                return;
            }

            try {
                double value = ExpressionParser.evaluate(input);
                sender.sendMessage(CommandMessages.success("= " + NUMBER.format(value)));
            } catch (IllegalArgumentException exception) {
                sender.sendMessage(CommandMessages.error(exception.getMessage()));
            }
        }, inputArg);
    }

    @Override
    public String permission() {
        return "fallnight.command.eval";
    }

    @Override
    public String summary() {
        return "Evaluate arithmetic or run a console command.";
    }

    @Override
    public String usage() {
        return "/eval <expression...>";
    }

    private static final class ExpressionParser {
        private final String input;
        private int index;

        private ExpressionParser(String input) {
            this.input = input;
        }

        static double evaluate(String input) {
            ExpressionParser parser = new ExpressionParser(input);
            double result = parser.parseExpression();
            parser.skipWhitespace();
            if (!parser.isAtEnd()) {
                throw new IllegalArgumentException("Unexpected token at position " + (parser.index + 1) + ".");
            }
            if (Double.isNaN(result) || Double.isInfinite(result)) {
                throw new IllegalArgumentException("Expression result is not a finite number.");
            }
            return result;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value += parseTerm();
                } else if (match('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parseFactor();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value *= parseFactor();
                } else if (match('/')) {
                    double divisor = parseFactor();
                    if (divisor == 0D) {
                        throw new IllegalArgumentException("Division by zero is not allowed.");
                    }
                    value /= divisor;
                } else {
                    return value;
                }
            }
        }

        private double parseFactor() {
            skipWhitespace();
            if (match('+')) {
                return parseFactor();
            }
            if (match('-')) {
                return -parseFactor();
            }
            if (match('(')) {
                double value = parseExpression();
                skipWhitespace();
                if (!match(')')) {
                    throw new IllegalArgumentException("Missing closing ')' at position " + (index + 1) + ".");
                }
                return value;
            }
            return parseNumber();
        }

        private double parseNumber() {
            skipWhitespace();
            int start = index;
            while (!isAtEnd()) {
                char c = current();
                if ((c >= '0' && c <= '9') || c == '.') {
                    index++;
                    continue;
                }
                break;
            }
            if (start == index) {
                throw new IllegalArgumentException("Expected a number at position " + (index + 1) + ".");
            }
            String token = input.substring(start, index);
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid number '" + token + "'.");
            }
        }

        private boolean match(char expected) {
            if (isAtEnd() || current() != expected) {
                return false;
            }
            index++;
            return true;
        }

        private void skipWhitespace() {
            while (!isAtEnd() && Character.isWhitespace(current())) {
                index++;
            }
        }

        private char current() {
            return input.charAt(index);
        }

        private boolean isAtEnd() {
            return index >= input.length();
        }
    }
}
