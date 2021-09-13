package cn.apisium.papershelled.services;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import java.util.HashMap;
import java.util.Map;

public final class GlobalProperty implements IGlobalPropertyService {
    private final Map<String, IPropertyKey> keys = new HashMap<>();
    private final HashMap<IPropertyKey, Object> map = new HashMap<>();
    static final class Key implements IPropertyKey { }

    @Override
    public IPropertyKey resolveKey(String name) {
        return keys.computeIfAbsent(name, key -> new Key());
    }

    @Override
    public <T> T getProperty(IPropertyKey key) {
        return getProperty(key, null);
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        map.put(key, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        return (T) map.getOrDefault(key, defaultValue);
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        return getProperty(key, defaultValue);
    }
}
