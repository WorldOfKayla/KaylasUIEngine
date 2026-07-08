package org.takesome.kaylasEngine.game.argsReader.libraries;

import java.util.List;

public class Library {
    private String name;
    private List<Rule> rules;
    private Artifact artifact;

    public String getName() {
        return name;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public Artifact getArtifact() {
        return artifact;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRules(List<Rule> rules) {
        this.rules = rules;
    }

    public void setArtifact(Artifact artifact) {
        this.artifact = artifact;
    }
}

class Rule {
    private String action;
    private OS os;

    public String getAction() {
        return action;
    }

    public OS getOs() {
        return os;
    }
}

class OS {
    private String name;

    public String getName() {
        return name;
    }
}

class Artifact {
    private String sha1;
    private int size;
    private String path;
    private String url;

    public Artifact(String sha1, int size, String path, String url) {
        this.path = path;
        this.url = url;
        this.sha1 = sha1;
        this.size = size;
    }

    public String getSha1() {
        return sha1;
    }

    public int getSize() {
        return size;
    }

    public String getPath() {
        return path;
    }

    public String getUrl() {
        return url;
    }

    public void setSha1(String sha1) {
        this.sha1 = sha1;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
