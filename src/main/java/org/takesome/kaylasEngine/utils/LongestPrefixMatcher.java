package org.takesome.kaylasEngine.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Trie-backed longest-prefix matcher.
 *
 * <p>Lookup complexity depends on the input prefix length rather than on the number of registered
 * masks, which makes it suitable for resolving file-path rules in large manifests.</p>
 */
public final class LongestPrefixMatcher<V> {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private Node<V> root = new Node<>();
    private int size;

    public void replaceAll(Map<String, ? extends V> entries) {
        Objects.requireNonNull(entries, "entries");
        Node<V> replacementRoot = new Node<>();
        int replacementSize = 0;
        for (Map.Entry<String, ? extends V> entry : entries.entrySet()) {
            String prefix = normalizePrefix(entry.getKey());
            putInto(replacementRoot, prefix, entry.getValue());
            replacementSize++;
        }

        lock.writeLock().lock();
        try {
            root = replacementRoot;
            size = replacementSize;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(String prefix, V value) {
        String normalizedPrefix = normalizePrefix(prefix);
        lock.writeLock().lock();
        try {
            if (putInto(root, normalizedPrefix, value)) {
                size++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Optional<V> match(CharSequence input) {
        if (input == null || input.length() == 0) {
            return Optional.empty();
        }

        lock.readLock().lock();
        try {
            Node<V> current = root;
            V bestMatch = current.hasValue ? current.value : null;
            boolean found = current.hasValue;

            for (int index = 0; index < input.length(); index++) {
                current = current.children.get(input.charAt(index));
                if (current == null) {
                    break;
                }
                if (current.hasValue) {
                    bestMatch = current.value;
                    found = true;
                }
            }
            return found ? Optional.ofNullable(bestMatch) : Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            root = new Node<>();
            size = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return size;
        } finally {
            lock.readLock().unlock();
        }
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            throw new IllegalArgumentException("Prefix must not be null or empty.");
        }
        return prefix;
    }

    private static <V> boolean putInto(Node<V> root, String prefix, V value) {
        Node<V> current = root;
        for (int index = 0; index < prefix.length(); index++) {
            current = current.children.computeIfAbsent(prefix.charAt(index), ignored -> new Node<>());
        }
        boolean added = !current.hasValue;
        current.value = value;
        current.hasValue = true;
        return added;
    }

    private static final class Node<V> {
        private final Map<Character, Node<V>> children = new HashMap<>();
        private V value;
        private boolean hasValue;
    }
}
