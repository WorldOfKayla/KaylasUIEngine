package org.takesome.kaylasEngine.gui.command;

import java.awt.event.ActionEvent;
import java.util.function.Consumer;

/**
 * Interface for simple dynamic command registration.
 * Allows registering, removing, and executing commands by their key.
 */
public interface DynamicCommandRegistry {

    /**
     * Registers a command with a unique key.
     *
     * @param key     unique identifier of the command
     * @param command lambda expression or method implementing the command handler
     */
    void registerCommand(String key, Consumer<ActionEvent> command);

    /**
     * Removes a command from the registry by the specified key.
     *
     * @param key unique identifier of the command
     */
    void unregisterCommand(String key);

    /**
     * Executes the command registered with the given key.
     *
     * @param key   unique identifier of the command
     * @param event event that triggered the command execution
     */
    void executeCommand(String key, ActionEvent event);
}
