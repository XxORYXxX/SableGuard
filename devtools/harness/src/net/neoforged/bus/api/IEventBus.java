package net.neoforged.bus.api; public interface IEventBus { <T extends Event> void addListener(java.util.function.Consumer<T> c); }
