package net.neoforged.neoforgespi.language;
public interface IModInfo { org.apache.maven.artifact.versioning.ArtifactVersion getVersion(); String getModId(); String getDisplayName(); IModFileInfo getOwningFile(); }
