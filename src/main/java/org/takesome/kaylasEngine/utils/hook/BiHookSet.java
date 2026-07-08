package org.takesome.kaylasEngine.utils.hook;

import org.takesome.kaylasEngine.Engine;

import java.util.HashSet;
import java.util.Set;

public class BiHookSet<V, R> {
    public final Set<Hook<V, R>> list = new HashSet<>();

    public void registerHook(Hook<V, R> hook) {
        list.add(hook);
    }

    public void unregisterHook(Hook<V, R> hook) {
        list.remove(hook);
    }

    /**
     * @param context custom param
     * @param object  custom param
     * @return True if hook to interrupt processing
     * False to continue
     * @throws HookException The hook may return the error text throwing this exception
     */
    public boolean hook(V context, R object) throws HookException {
        for (Hook<V, R> hook : list) {
            try {
                if (hook.hook(context, object)) return true;
            } catch (HookException e) {
                Engine.LOGGER.error("Hook execution failed: " + e.getMessage());
                throw e;
            }
        }
        return false;
    }

    @FunctionalInterface
    public interface Hook<V, R> {
        /**
         * @param context custom param
         * @param object  custom param
         * @return True if you need to interrupt hook processing
         * False to continue processing hook
         * @throws HookException The hook may return the error text throwing this exception
         */
        boolean hook(V object, R context) throws HookException;
    }
}